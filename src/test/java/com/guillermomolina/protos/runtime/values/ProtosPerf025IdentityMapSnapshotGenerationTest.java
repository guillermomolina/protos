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

package com.guillermomolina.protos.runtime.values;

import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.numeric.ProtosNumericHashKey;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PERF025 focal evidence for generation-backed shallow IdentityMap snapshots.
 *
 * <p>Snapshot publication must not copy the insertion-order Entry sequence or
 * materialize one association pair per entry. Once either snapshot view is
 * published, the first later mutation detaches the live IdentityMap to fresh
 * Entry objects and fresh exact-hash buckets, preserving every established
 * snapshot without imposing copy-on-write cost on never-snapshotted maps.
 */
final class ProtosPerf025IdentityMapSnapshotGenerationTest {
    private static final Path IDENTITY_MAP_SOURCE =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/runtime/values/"
                            + "ProtosIdentityMapValue.java");
    private static final Path BYTECODE_ROOT =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/bytecode/activation/structured/"
                            + "ProtosStructuredEachCalls.java");

    @Test
    void unpublishedMutationStaysInPlaceAndSnapshotsReuseViews()
            throws Exception {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosIdentityMapValue map =
                new ProtosIdentityMapValue(parent);

        Object key = new ProtosObjectValue(parent);
        Object firstValue = new ProtosObjectValue(parent);
        Object replacement = new ProtosObjectValue(parent);
        ProtosNumericHashKey hash = ProtosNumericHashKey.fromIdentity(key);

        map.append(key, hash, firstValue);

        Object initialGeneration = generation(map);

        List<ProtosIdentityMapValue.Entry> candidates =
                map.candidatesForRecordedIdentityHash(hash);

        map.replaceValue(candidates.get(0), replacement);

        assertSame(
                initialGeneration,
                generation(map),
                "ordinary replacement before publication must not detach");
        assertSame(
                candidates,
                map.candidatesForRecordedIdentityHash(hash),
                "unpublished exact-hash bucket view remains live");
        assertSame(replacement, candidates.get(0).value());

        List<ProtosIdentityMapValue.Entry> keyed =
                map.keyedSnapshot();
        List<Map.Entry<Object, Object>> associations =
                map.associationSnapshot();

        assertSame(initialGeneration, generation(map));

        assertSame(
                keyed,
                map.keyedSnapshot(),
                "repeated keyed snapshots reuse the generation view");
        assertSame(
                associations,
                map.associationSnapshot(),
                "repeated association snapshots reuse the generation view");

        assertSame(
                keyed.get(0),
                associations.get(0),
                "association traversal must not materialize pair objects");

        assertThrows(
                UnsupportedOperationException.class,
                keyed::clear);
        assertThrows(
                UnsupportedOperationException.class,
                () -> associations.get(0).setValue(firstValue));
    }

    @Test
    void successivePublishedGenerationsKeepExactHistoricalValues()
            throws Exception {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosIdentityMapValue map =
                new ProtosIdentityMapValue(parent);

        Object key = new ProtosObjectValue(parent);
        Object first = new ProtosObjectValue(parent);
        Object second = new ProtosObjectValue(parent);
        Object third = new ProtosObjectValue(parent);
        Object fourth = new ProtosObjectValue(parent);
        ProtosNumericHashKey hash = ProtosNumericHashKey.fromIdentity(key);

        map.append(key, hash, first);

        List<Map.Entry<Object, Object>> firstSnapshot =
                map.associationSnapshot();
        Object firstGeneration = generation(map);

        map.replaceValue(
                map.candidatesForRecordedIdentityHash(hash).get(0),
                second);

        Object secondGeneration = generation(map);

        assertNotSame(firstGeneration, secondGeneration);
        assertSame(first, firstSnapshot.get(0).getValue());
        assertSame(
                second,
                map.candidatesForRecordedIdentityHash(hash)
                        .get(0)
                        .value());

        List<Map.Entry<Object, Object>> secondSnapshot =
                map.associationSnapshot();

        map.replaceValue(
                map.candidatesForRecordedIdentityHash(hash).get(0),
                third);

        Object thirdGeneration = generation(map);

        assertNotSame(secondGeneration, thirdGeneration);
        assertSame(first, firstSnapshot.get(0).getValue());
        assertSame(second, secondSnapshot.get(0).getValue());
        assertSame(
                third,
                map.candidatesForRecordedIdentityHash(hash)
                        .get(0)
                        .value());

        map.replaceValue(
                map.candidatesForRecordedIdentityHash(hash).get(0),
                fourth);

        assertSame(
                thirdGeneration,
                generation(map),
                "after detach, ordinary replacement stays cheap until "
                        + "another snapshot is published");
        assertSame(first, firstSnapshot.get(0).getValue());
        assertSame(second, secondSnapshot.get(0).getValue());
        assertSame(
                fourth,
                map.candidatesForRecordedIdentityHash(hash)
                        .get(0)
                        .value());
    }

    @Test
    void appendRemoveAndReinsertPreserveOldOrderAndCollisionBuckets()
            throws Exception {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosIdentityMapValue map =
                new ProtosIdentityMapValue(parent);

        Object firstKey = new ProtosObjectValue(parent);
        Object secondKey = new ProtosObjectValue(parent);
        Object firstValue = new ProtosObjectValue(parent);
        Object secondValue = new ProtosObjectValue(parent);
        Object reinsertedValue = new ProtosObjectValue(parent);

        ProtosNumericHashKey collisionHash = ProtosNumericHashKey.ofLong(17);

        map.append(firstKey, collisionHash, firstValue);

        List<ProtosIdentityMapValue.Entry> oneEntrySnapshot =
                map.keyedSnapshot();
        Object firstGeneration = generation(map);

        map.append(secondKey, collisionHash, secondValue);

        Object secondGeneration = generation(map);

        assertNotSame(firstGeneration, secondGeneration);
        assertEquals(1, oneEntrySnapshot.size());
        assertSame(firstKey, oneEntrySnapshot.get(0).key());

        List<ProtosIdentityMapValue.Entry> twoEntrySnapshot =
                map.keyedSnapshot();

        List<ProtosIdentityMapValue.Entry> publishedCandidates =
                map.candidatesForRecordedIdentityHash(collisionHash);

        assertEquals(2, publishedCandidates.size());
        assertSame(firstKey, publishedCandidates.get(0).key());
        assertSame(secondKey, publishedCandidates.get(1).key());

        ProtosIdentityMapValue.Entry removed =
                publishedCandidates.get(0);

        assertSame(firstValue, map.remove(removed));

        Object thirdGeneration = generation(map);

        assertNotSame(secondGeneration, thirdGeneration);

        assertEquals(2, twoEntrySnapshot.size());
        assertSame(firstKey, twoEntrySnapshot.get(0).key());
        assertSame(secondKey, twoEntrySnapshot.get(1).key());

        assertEquals(
                2,
                publishedCandidates.size(),
                "old published collision bucket remains immutable");

        List<ProtosIdentityMapValue.Entry> currentCandidates =
                map.candidatesForRecordedIdentityHash(collisionHash);

        assertEquals(1, currentCandidates.size());
        assertSame(secondKey, currentCandidates.get(0).key());

        map.append(
                firstKey,
                collisionHash,
                reinsertedValue);

        assertSame(
                thirdGeneration,
                generation(map),
                "after detach, append stays in-place until publication");
        assertSame(
                currentCandidates,
                map.candidatesForRecordedIdentityHash(collisionHash));

        assertEquals(2, currentCandidates.size());
        assertSame(secondKey, currentCandidates.get(0).key());
        assertSame(firstKey, currentCandidates.get(1).key());

        List<ProtosIdentityMapValue.Entry> currentOrder =
                map.keyedSnapshot();

        assertEquals(2, currentOrder.size());
        assertSame(secondKey, currentOrder.get(0).key());
        assertSame(firstKey, currentOrder.get(1).key());
        assertSame(reinsertedValue, currentOrder.get(1).value());

        assertEquals(1, oneEntrySnapshot.size());
        assertEquals(2, twoEntrySnapshot.size());
        assertSame(firstValue, twoEntrySnapshot.get(0).value());
    }

    @Test
    void sourceKeepsSnapshotsLazyAndStructuredEachRetainsView()
            throws Exception {
        String identitySource =
                Files.readString(IDENTITY_MAP_SOURCE);
        String bytecodeSource =
                Files.readString(BYTECODE_ROOT);

        assertTrue(
                identitySource.contains(
                        "Arrays.asList(entries).subList(0, size)"));
        assertTrue(
                identitySource.contains(
                        "new AssociationSnapshotView("));

        assertFalse(
                identitySource.contains(
                        "return List.copyOf(entries);"),
                "keyedSnapshot must not restore eager O(n) copying");

        assertFalse(
                identitySource.contains(
                        "snapshot.add(Map.entry("),
                "associationSnapshot must not allocate one pair per entry");

        int identityMapEachStart =
                bytecodeSource.indexOf(
                        "static final class PreparedIdentityMapEachCall");
        int identityMapEachEnd =
                bytecodeSource.indexOf(
                        "static final class PreparedMapEachCall",
                        identityMapEachStart);

        assertTrue(
                identityMapEachStart >= 0,
                "PreparedIdentityMapEachCall source owner must remain present");
        assertTrue(
                identityMapEachEnd > identityMapEachStart,
                "PreparedIdentityMapEachCall source boundary must remain present");

        String identityMapEachSource =
                bytecodeSource.substring(
                        identityMapEachStart,
                        identityMapEachEnd);

        assertFalse(
                identityMapEachSource.contains(
                        "List.copyOf(value.associationSnapshot())"),
                "structured IdentityMap.each must retain the stable "
                        + "generation view directly");

        assertTrue(
                identityMapEachSource.contains(
                        "this.snapshot = value.associationIndexedSnapshot();"));
    }

    private static Object generation(
            ProtosIdentityMapValue map)
            throws Exception {
        Field field =
                ProtosIdentityMapValue.class.getDeclaredField(
                        "generation");
        field.setAccessible(true);
        return field.get(map);
    }

    @Test
    void openIndexSurvivesGrowthCollisionsAndRemoval() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosIdentityMapValue map = new ProtosIdentityMapValue(parent);
        int count = 200;
        Object[] keys = new Object[count];
        Object[] values = new Object[count];
        for (int index = 0; index < count; index++) {
            keys[index] = new ProtosObjectValue(parent);
            values[index] = new ProtosObjectValue(parent);
            // Every fourth key shares one recorded hash bucket.
            long hash = index % 4 == 0 ? 7L : 1000L + index;
            map.append(keys[index], ProtosNumericHashKey.ofLong(hash), values[index]);
        }

        assertEquals(count, map.keyedSize());
        List<ProtosIdentityMapValue.Entry> collided =
                map.candidatesForRecordedIdentityHash(ProtosNumericHashKey.ofLong(7L));
        assertEquals(count / 4, collided.size());
        for (int index = 0; index < collided.size(); index++) {
            assertSame(keys[index * 4], collided.get(index).key());
        }

        // Exact physical removal of a whole singleton bucket and one collided entry.
        ProtosIdentityMapValue.Entry singleton =
                map.candidatesForRecordedIdentityHash(ProtosNumericHashKey.ofLong(1001L)).get(0);
        assertSame(values[1], map.remove(singleton));
        assertTrue(map.candidatesForRecordedIdentityHash(ProtosNumericHashKey.ofLong(1001L)).isEmpty());
        ProtosIdentityMapValue.Entry middle = collided.get(1);
        assertSame(values[4], map.remove(middle));

        List<ProtosIdentityMapValue.Entry> remaining =
                map.candidatesForRecordedIdentityHash(ProtosNumericHashKey.ofLong(7L));
        assertEquals(count / 4 - 1, remaining.size());
        assertSame(keys[0], remaining.get(0).key());
        assertSame(keys[8], remaining.get(1).key());
        assertThrows(IllegalStateException.class, () -> map.remove(middle));

        // Reinsertion lands at the end of insertion order, in its bucket's tail.
        map.append(keys[1], ProtosNumericHashKey.ofLong(1001L), values[1]);
        List<ProtosIdentityMapValue.Entry> snapshot = map.keyedSnapshot();
        assertEquals(count - 1, snapshot.size());
        assertSame(keys[0], snapshot.get(0).key());
        assertSame(keys[2], snapshot.get(1).key());
        assertSame(keys[1], snapshot.get(snapshot.size() - 1).key());

        // A published generation is retained while later mutation detaches.
        map.remove(map.candidatesForRecordedIdentityHash(ProtosNumericHashKey.ofLong(1002L)).get(0));
        assertEquals(count - 1, snapshot.size());
        assertSame(keys[2], snapshot.get(1).key());
        assertEquals(count - 2, map.keyedSize());
    }

    @Test
    void indexedLookupFollowsDetachedGenerationsAndKeepsSnapshots() {
        ProtosObjectValue parent = ProtosObjectValue.rootObject();
        ProtosIdentityMapValue map = new ProtosIdentityMapValue(parent);
        ProtosNumericHashKey shared = ProtosNumericHashKey.ofLong(7L);
        Object first = new ProtosObjectValue(parent);
        Object second = new ProtosObjectValue(parent);
        Object third = new ProtosObjectValue(parent);
        map.append(first, shared, "first");
        map.append(second, shared, "second");
        map.append(third, ProtosNumericHashKey.ofLong(8L), "third");

        assertSame(first, map.findIdentical(first, shared).key());
        assertSame(second, map.findIdentical(second, shared).key());
        assertNull(map.findIdentical(third, shared));
        assertNull(map.findIdentical(new ProtosObjectValue(parent), shared));

        List<ProtosIdentityMapValue.Entry> snapshot = map.keyedSnapshot();
        ProtosIdentityMapValue.Entry published = map.findIdentical(second, shared);
        assertSame(snapshot.get(1), published);

        // Removal after publication detaches; lookup sees only the live generation.
        map.remove(map.findIdentical(first, shared));
        assertNull(map.findIdentical(first, shared));
        assertSame(second, map.findIdentical(second, shared).key());
        assertNotSame(published, map.findIdentical(second, shared));

        assertEquals(3, snapshot.size());
        assertSame(first, snapshot.get(0).key());
        assertEquals("second", snapshot.get(1).value());
    }

    @Test
    void standardKeyLookupUsesTheBucketIndexWithoutListProjection()
            throws Exception {
        String identitySource = Files.readString(IDENTITY_MAP_SOURCE);
        int start = identitySource.indexOf(
                "private Entry find(Object key, ProtosNumericHashKey hash)");
        assertTrue(start >= 0, "Indexed generation lookup must exist");
        String lookup = identitySource.substring(
                start, identitySource.indexOf("        }\n\n", start));
        assertTrue(lookup.contains("lookupBucket(hash)"));
        assertTrue(lookup.contains("bucket.entries"));
        assertTrue(lookup.contains("ProtosIdentity.identical("));
        assertFalse(lookup.contains("bucket.view"));
        assertFalse(lookup.contains("List"));
        assertFalse(lookup.contains("fromIdentity"));

        String protocol = Files.readString(Path.of(
                "src/main/java/com/guillermomolina/protos/execution/protocols/values/"
                        + "ProtosStandardIdentityMapProtocol.java"));
        assertFalse(protocol.contains("candidatesForRecordedIdentityHash"));
        assertFalse(protocol.contains("associationSnapshot()"));
        assertTrue(protocol.contains("m.findIdentical(k,h)"));
        assertTrue(protocol.contains("associationIndexedSnapshot()"));
    }

    @Test
    void identityIndexUsesNoJavaHashMap() throws Exception {
        String source = Files.readString(IDENTITY_MAP_SOURCE);
        assertFalse(source.contains("HashMap"));
        assertFalse(source.contains("computeIfAbsent"));
        assertTrue(source.contains("(5L * index) + perturb + 1L"));
    }
}
