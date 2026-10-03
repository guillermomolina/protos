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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PERF025 focal evidence for pay-as-you-grow physical Map/IdentityMap indexing.
 *
 * <p>The semantic insertion-order sequence remains authoritative, while ordinary
 * keyed search selects only the bucket carrying the query's exact recorded
 * semantic hash. Snapshot representations used by iteration and Map.match are
 * deliberately outside this slice.
 */
final class ProtosPerf025MapPhysicalIndexTest {
    private static final Path MAP_PROTOCOL =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/"
                            + "ProtosStandardMapProtocol.java");
    private static final Path IDENTITY_MAP_PROTOCOL =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/"
                            + "ProtosStandardIdentityMapProtocol.java");
    private static final Path BYTECODE_ROOT =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/"
                            + "ProtosBytecodeRootNode.java");

    @Test
    void mapIndexPreservesCollisionOrderReplacementAndReinsertion() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosMapValue map = new ProtosMapValue(parent);

        Object firstKey = new ProtosObjectValue(parent);
        Object otherHashKey = new ProtosObjectValue(parent);
        Object secondCollisionKey = new ProtosObjectValue(parent);

        Object firstValue = new ProtosObjectValue(parent);
        Object replacementValue = new ProtosObjectValue(parent);
        Object otherHashValue = new ProtosObjectValue(parent);
        Object secondCollisionValue = new ProtosObjectValue(parent);
        Object reinsertedValue = new ProtosObjectValue(parent);

        BigInteger collisionHash = BigInteger.valueOf(7);
        BigInteger otherHash = BigInteger.valueOf(11);

        map.append(firstKey, collisionHash, firstValue);
        map.append(otherHashKey, otherHash, otherHashValue);
        map.append(secondCollisionKey, collisionHash, secondCollisionValue);

        List<ProtosMapValue.Entry> candidates =
                map.candidatesForRecordedHash(collisionHash);

        assertEquals(2, candidates.size());
        assertSame(firstKey, candidates.get(0).key());
        assertSame(secondCollisionKey, candidates.get(1).key());
        assertEquals(collisionHash, candidates.get(0).recordedHash());
        assertEquals(collisionHash, candidates.get(1).recordedHash());
        assertTrue(map.candidatesForRecordedHash(BigInteger.valueOf(13)).isEmpty());

        map.replaceValue(candidates.get(0), replacementValue);

        assertSame(
                candidates,
                map.candidatesForRecordedHash(collisionHash),
                "lookup must reuse the bucket view rather than allocate a search snapshot");
        assertSame(firstKey, candidates.get(0).key());
        assertSame(replacementValue, candidates.get(0).value());

        ProtosMapValue.Entry removed = candidates.get(0);
        assertSame(replacementValue, map.remove(removed));

        assertEquals(1, candidates.size());
        assertSame(secondCollisionKey, candidates.get(0).key());

        map.append(firstKey, collisionHash, reinsertedValue);

        assertEquals(2, candidates.size());
        assertSame(secondCollisionKey, candidates.get(0).key());
        assertSame(firstKey, candidates.get(1).key());

        List<ProtosMapValue.Entry> insertionOrder = map.keyedSnapshot();
        assertEquals(3, insertionOrder.size());
        assertSame(otherHashKey, insertionOrder.get(0).key());
        assertSame(secondCollisionKey, insertionOrder.get(1).key());
        assertSame(firstKey, insertionOrder.get(2).key());
        assertSame(reinsertedValue, insertionOrder.get(2).value());
    }

    @Test
    void identityMapIndexUsesSemanticIdentityHashWithoutJavaIdentityAssumption() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosIdentityMapValue map = new ProtosIdentityMapValue(parent);

        ProtosIntegerValue first =
                new ProtosIntegerValue(BigInteger.valueOf(42));
        ProtosIntegerValue semanticallyIdentical =
                new ProtosIntegerValue(BigInteger.valueOf(42));

        assertNotSame(first, semanticallyIdentical);
        assertTrue(ProtosIdentity.identical(first, semanticallyIdentical));

        BigInteger identityHash = ProtosIdentity.identityHash(first);
        assertEquals(
                identityHash,
                ProtosIdentity.identityHash(semanticallyIdentical));

        Object firstValue = new ProtosObjectValue(parent);
        Object secondValue = new ProtosObjectValue(parent);
        Object collisionKey = new ProtosObjectValue(parent);

        map.append(first, identityHash, firstValue);
        map.append(collisionKey, identityHash, secondValue);

        List<ProtosIdentityMapValue.Entry> candidates =
                map.candidatesForRecordedIdentityHash(identityHash);

        assertEquals(2, candidates.size());
        assertSame(first, candidates.get(0).key());
        assertSame(collisionKey, candidates.get(1).key());
        assertTrue(
                ProtosIdentity.identical(
                        semanticallyIdentical,
                        candidates.get(0).key()));

        ProtosIdentityMapValue.Entry removed = candidates.get(0);
        assertSame(firstValue, map.remove(removed));
        assertEquals(1, candidates.size());
        assertSame(collisionKey, candidates.get(0).key());

        map.append(first, identityHash, firstValue);

        assertEquals(2, candidates.size());
        assertSame(collisionKey, candidates.get(0).key());
        assertSame(first, candidates.get(1).key());
    }

    @Test
    void productiveSearchOwnersCannotRegressToWholeMapSnapshots() throws Exception {
        String mapProtocol = Files.readString(MAP_PROTOCOL);
        String identityMapProtocol = Files.readString(IDENTITY_MAP_PROTOCOL);
        String bytecodeRoot = Files.readString(BYTECODE_ROOT);

        assertTrue(
                mapProtocol.contains(
                        "for(var e:m.candidatesForRecordedHash(h))"));
        assertFalse(
                mapProtocol.contains(
                        "for(var e:m.keyedSnapshot())"));

        assertTrue(
                identityMapProtocol.contains(
                        "m.candidatesForRecordedIdentityHash(h)"));
        assertFalse(
                identityMapProtocol.contains(
                        "m.keyedSnapshot()"));

        assertFalse(
                bytecodeRoot.contains(
                        "List.copyOf(map.keyedSnapshot())"));
        assertFalse(
                bytecodeRoot.contains(
                        "ProtosStandardMapProtocol.stableSnapshot(map)"));

        assertEquals(
                4,
                occurrences(
                        bytecodeRoot,
                        "candidates = map.candidatesForRecordedHash(queryHash);"));

        assertTrue(
                mapProtocol.contains(
                        "List<ProtosMapValue.Entry> entries = map.keyedSnapshot();"),
                "Map.match stable snapshot remains intentionally independent");
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int from = 0;

        while (true) {
            int found = source.indexOf(needle, from);
            if (found < 0) {
                return count;
            }
            count++;
            from = found + needle.length();
        }
    }
}
