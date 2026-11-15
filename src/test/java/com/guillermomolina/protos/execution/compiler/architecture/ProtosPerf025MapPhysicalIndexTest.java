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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.numeric.ProtosNumericHashKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
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
                    "src/main/java/com/guillermomolina/protos/execution/protocols/values/"
                            + "ProtosStandardMapProtocol.java");
    private static final Path IDENTITY_MAP_PROTOCOL =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/protocols/values/"
                            + "ProtosStandardIdentityMapProtocol.java");
    private static final Path MAP_QUERY_CALLS =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/bytecode/activation/structured/"
                            + "ProtosStructuredMapQueryCalls.java");
    private static final Path MAP_UPDATE_CALLS =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/bytecode/activation/structured/"
                            + "ProtosStructuredMapUpdateCalls.java");

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

        ProtosNumericHashKey collisionHash = ProtosNumericHashKey.ofLong(7);
        ProtosNumericHashKey otherHash = ProtosNumericHashKey.ofLong(11);

        map.append(firstKey, collisionHash, firstValue);
        map.append(otherHashKey, otherHash, otherHashValue);
        map.append(secondCollisionKey, collisionHash, secondCollisionValue);

        ProtosMapValue.Candidates candidates =
                map.candidatesForRecordedHash(collisionHash);

        assertEquals(2, candidates.size());
        assertSame(firstKey, candidates.get(0).key());
        assertSame(secondCollisionKey, candidates.get(1).key());
        assertEquals(collisionHash, candidates.get(0).recordedHash());
        assertEquals(collisionHash, candidates.get(1).recordedHash());
        assertTrue(map.candidatesForRecordedHash(ProtosNumericHashKey.ofLong(13)).isEmpty());

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
    void physicalRemovalIsByEntryIdentityNeverByEquality() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosMapValue map = new ProtosMapValue(parent);
        ProtosMapValue other = new ProtosMapValue(parent);
        ProtosNumericHashKey hash = ProtosNumericHashKey.ofLong(5);
        Object key = new ProtosObjectValue(parent);
        Object value = new ProtosObjectValue(parent);

        map.append(key, hash, value);
        other.append(key, hash, value);
        ProtosMapValue.Entry owned = map.candidatesForRecordedHash(hash).get(0);
        ProtosMapValue.Entry foreign = other.candidatesForRecordedHash(hash).get(0);

        // Same key, hash and value objects: only the physical identity differs.
        assertSame(owned.key(), foreign.key());
        assertThrows(IllegalStateException.class, () -> map.remove(foreign));
        assertEquals(1, map.keyedSize());

        assertSame(value, map.remove(owned));
        assertEquals(0, map.keyedSize());
        assertTrue(map.candidatesForRecordedHash(hash).isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> map.remove(owned),
                "a removed entry is no longer a physical entry of the Map");
        assertEquals(1, other.keyedSize());
    }

    @Test
    void indexGrowthAndOrderCompactionPreserveInsertionOrderAndRepresentatives() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosMapValue map = new ProtosMapValue(parent);
        int count = 200;
        Object[] keys = new Object[count];
        Object[] values = new Object[count];
        for (int index = 0; index < count; index++) {
            keys[index] = new ProtosObjectValue(parent);
            values[index] = new ProtosObjectValue(parent);
            // Two physical entries per recorded hash exercise collisions after rehash.
            map.append(keys[index], ProtosNumericHashKey.ofLong(index / 2), values[index]);
        }
        List<ProtosMapValue.Entry> before = map.keyedSnapshot();
        for (int index = 0; index < count; index += 3) {
            assertSame(values[index], map.remove(before.get(index)));
        }
        Object appendedKey = new ProtosObjectValue(parent);
        Object appendedValue = new ProtosObjectValue(parent);
        map.append(appendedKey, ProtosNumericHashKey.ofLong(0), appendedValue);

        List<ProtosMapValue.Entry> after = map.keyedSnapshot();
        int position = 0;
        for (int index = 0; index < count; index++) {
            if (index % 3 == 0) {
                continue;
            }
            assertSame(before.get(index), after.get(position), "entry identity is preserved");
            assertSame(keys[index], after.get(position).key());
            assertSame(values[index], after.get(position).value());
            position++;
        }
        assertSame(appendedKey, after.get(position).key());
        assertEquals(position + 1, map.keyedSize());

        for (int index = 0; index < count; index++) {
            ProtosMapValue.Candidates candidates =
                    map.candidatesForRecordedHash(ProtosNumericHashKey.ofLong(index / 2));
            boolean present = index % 3 != 0;
            boolean found = false;
            for (int candidate = 0; candidate < candidates.size(); candidate++) {
                found |= candidates.get(candidate).key() == keys[index];
            }
            assertEquals(present, found, "bucket membership of key " + index);
        }
    }

    @Test
    void exactLargeHashesAreNeverTruncatedOrConflatedWithSmallHashes() throws Exception {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosMapValue map = new ProtosMapValue(parent);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
        BigInteger exact = BigInteger.ONE.shiftLeft(64);
        ProtosNumericHashKey large =
                ProtosNumericHashKey.fromSemanticInteger(
                        ProtosNumericValueSupport.integerFromHost(exact, prelude));
        // The same Java hash code as the large key, but a different exact hash.
        ProtosNumericHashKey small = ProtosNumericHashKey.ofLong(exact.hashCode());
        // The truncated low 64 bits of the large hash.
        ProtosNumericHashKey truncated = ProtosNumericHashKey.ofLong(exact.longValue());
        assertEquals(large.hashCode(), small.hashCode());

        Object largeKey = new ProtosObjectValue(parent);
        Object smallKey = new ProtosObjectValue(parent);
        map.append(largeKey, large, new ProtosObjectValue(parent));
        map.append(smallKey, small, new ProtosObjectValue(parent));

        assertEquals(1, map.candidatesForRecordedHash(large).size());
        assertSame(largeKey, map.candidatesForRecordedHash(large).get(0).key());
        assertEquals(1, map.candidatesForRecordedHash(small).size());
        assertSame(smallKey, map.candidatesForRecordedHash(small).get(0).key());
        assertTrue(map.candidatesForRecordedHash(truncated).isEmpty());
        assertEquals(
                large,
                ProtosNumericHashKey.fromSemanticInteger(
                        ProtosNumericValueSupport.integerFromHost(exact, prelude)));
    }

    @Test
    void associationIndexedSnapshotIsIndependentOfLaterMutation() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosMapValue map = new ProtosMapValue(parent);
        Object first = new ProtosObjectValue(parent);
        Object second = new ProtosObjectValue(parent);
        Object firstValue = new ProtosObjectValue(parent);
        Object secondValue = new ProtosObjectValue(parent);
        map.append(first, ProtosNumericHashKey.ofLong(1), firstValue);
        map.append(second, ProtosNumericHashKey.ofLong(2), secondValue);

        com.guillermomolina.protos.runtime.ProtosIndexedSnapshot snapshot =
                map.associationIndexedSnapshot();
        ProtosMapValue.Entry firstEntry = map.candidatesForRecordedHash(ProtosNumericHashKey.ofLong(1)).get(0);
        map.replaceValue(firstEntry, new ProtosObjectValue(parent));
        map.remove(map.candidatesForRecordedHash(ProtosNumericHashKey.ofLong(2)).get(0));
        map.append(new ProtosObjectValue(parent), ProtosNumericHashKey.ofLong(3), new ProtosObjectValue(parent));

        assertEquals(2, snapshot.length());
        assertSame(first, snapshot.keyAt(0));
        assertSame(firstValue, snapshot.valueAt(0));
        assertSame(second, snapshot.keyAt(1));
        assertSame(secondValue, snapshot.valueAt(1));
    }

    @Test
    void physicalStorageUsesNoJavaCollectionOrEquality() throws Exception {
        String storage =
                Files.readString(
                        Path.of(
                                "src/main/java/com/guillermomolina/protos/runtime/values/"
                                        + "ProtosMapValue.java"));
        String operational =
                storage.substring(
                        storage.indexOf("public Object remove(Entry e)"),
                        storage.indexOf("// ---- insertion order"));
        for (String forbidden : List.of(
                "indexOf(", ".contains(", "HashMap", "ArrayList", "List<", ".equals(")) {
            assertFalse(
                    operational.contains(forbidden),
                    "physical removal path must not use " + forbidden);
        }
        assertFalse(storage.contains("Map<ProtosNumericHashKey"));
    }

    @Test
    void identityMapIndexUsesSemanticIdentityHashWithoutJavaIdentityAssumption() throws Exception {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosIdentityMapValue map = new ProtosIdentityMapValue(parent);

        // Independently minted large values retain the distinct-representation
        // witness without depending on a primitive box cache's allocation policy.
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
        BigInteger exact = BigInteger.ONE.shiftLeft(64);
        Object first = ProtosNumericValueSupport.integerFromHost(exact, prelude);
        Object semanticallyIdentical = ProtosNumericValueSupport.integerFromHost(exact, prelude);

        assertNotSame(first, semanticallyIdentical);
        assertTrue(ProtosIdentity.identical(first, semanticallyIdentical));

        ProtosNumericHashKey identityHash = ProtosNumericHashKey.fromIdentity(first);
        assertEquals(
                identityHash,
                ProtosNumericHashKey.fromIdentity(semanticallyIdentical));

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
        String bytecodeRoot =
                Files.readString(MAP_QUERY_CALLS) + Files.readString(MAP_UPDATE_CALLS);

        assertTrue(
                mapProtocol.contains(
                        "ProtosMapValue.Candidates candidates=m.candidatesForRecordedHash(h);"));
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
