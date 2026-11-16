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


import com.guillermomolina.protos.runtime.numeric.ProtosNumericHashKey;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;

public final class ProtosIdentityMapValue extends ProtosObjectValue {
    /**
     * One keyed association. Entry values remain mutable only while their
     * owning generation has never been published as a snapshot.
     */
    public static final class Entry implements Map.Entry<Object, Object> {
        private final Object key;
        private final ProtosNumericHashKey hash;
        private Object value;

        Entry(Object key, ProtosNumericHashKey hash, Object value) {
            this.key = Objects.requireNonNull(key, "key");
            this.hash = Objects.requireNonNull(hash, "hash");
            this.value = Objects.requireNonNull(value, "value");
        }

        public Object key() {
            return key;
        }

        public ProtosNumericHashKey recordedIdentityHash() {
            return hash;
        }

        public Object value() {
            return value;
        }

        void value(Object value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public Object setValue(Object value) {
            throw new UnsupportedOperationException(
                    "IdentityMap snapshot entries are read-only");
        }
    }

    private static final int EMPTY_INDEX = 0;
    private static final int DUMMY_INDEX = -1;
    private static final int INITIAL_INDEX_SIZE = 8;
    private static final int PERTURB_SHIFT = 5;

    /**
     * Entries sharing one exact recorded identity hash, in insertion order.
     * Owned by exactly one generation.
     */
    private static final class Bucket {
        private static final Entry[] NO_BUCKET_ENTRIES = new Entry[0];

        private final ProtosNumericHashKey hash;
        private final int hashCode;
        private Entry[] entries = NO_BUCKET_ENTRIES;
        private int count;
        /* Position in the owning generation's bucket array, or -1 once removed. */
        private int bucketIndex = -1;
        private final List<Entry> view = new BucketView(this);

        private Bucket(ProtosNumericHashKey hash, int hashCode) {
            this.hash = hash;
            this.hashCode = hashCode;
        }

        private void add(Entry entry) {
            if (count == entries.length) {
                entries = Arrays.copyOf(entries, Math.max(2, count * 2));
            }
            entries[count++] = entry;
        }

        private boolean removeIdentical(Entry entry) {
            for (int index = 0; index < count; index++) {
                if (entries[index] == entry) {
                    int tail = count - index - 1;
                    if (tail > 0) {
                        System.arraycopy(entries, index + 1, entries, index, tail);
                    }
                    entries[--count] = null;
                    return true;
                }
            }
            return false;
        }
    }

    /** Read-only live projection of one bucket. */
    private static final class BucketView
            extends AbstractList<Entry>
            implements RandomAccess {
        private final Bucket bucket;

        private BucketView(Bucket bucket) {
            this.bucket = bucket;
        }

        @Override
        public Entry get(int index) {
            Objects.checkIndex(index, bucket.count);
            return bucket.entries[index];
        }

        @Override
        public int size() {
            return bucket.count;
        }
    }

    /**
     * Allocation-free association projection over one published generation.
     * The projected Map.Entry is the generation-owned Entry itself.
     */
    private static final class AssociationSnapshotView
            extends AbstractList<Map.Entry<Object, Object>>
            implements RandomAccess {
        private final List<Entry> entries;

        private AssociationSnapshotView(List<Entry> entries) {
            this.entries = Objects.requireNonNull(entries, "entries");
        }

        @Override
        public Map.Entry<Object, Object> get(int index) {
            return entries.get(index);
        }

        @Override
        public int size() {
            return entries.size();
        }
    }

    /**
     * PERF025 IdentityMap insertion-order state plus its exact recorded-hash
     * index.
     *
     * <p>Publishing either snapshot is O(1). Once published, neither the
     * sequence, its Entry values nor its buckets are mutated again. The first
     * later mutation creates fresh Entry objects and rebuilds the bucket index;
     * old generations stay alive only through ordinary snapshot references.
     */
    private static final class IdentityMapGeneration {
        private static final Entry[] NO_ENTRIES = new Entry[0];

        /* Insertion-ordered entries [0, size); read by index, never via List. */
        private Entry[] entries = NO_ENTRIES;
        private int size;

        /* Open-addressed index of 1-based bucket positions; null until first insertion. */
        private int[] indices;
        private Bucket[] buckets;
        private int bucketsUsed;

        private List<Entry> keyedSnapshotView;
        private List<Map.Entry<Object, Object>> associationSnapshotView;
        private ProtosIndexedSnapshot associationIndexedView;
        private boolean published;

        private void append(Entry entry) {
            if (size == entries.length) {
                entries = Arrays.copyOf(entries, Math.max(4, size * 2));
            }
            entries[size++] = entry;
            Bucket bucket = lookupBucket(entry.recordedIdentityHash());
            if (bucket == null) {
                bucket = insertBucket(entry.recordedIdentityHash());
            }
            bucket.add(entry);
        }

        private List<Entry> candidates(ProtosNumericHashKey hash) {
            Bucket bucket =
                    lookupBucket(
                            Objects.requireNonNull(hash, "hash"));
            return bucket == null ? List.of() : bucket.view;
        }

        private static int nextIndex(int index, long perturb, int mask) {
            return (int) (((5L * index) + perturb + 1L) & mask);
        }

        private Bucket lookupBucket(ProtosNumericHashKey hash) {
            int[] table = indices;
            if (table == null) {
                return null;
            }
            int hashCode = hash.hashCode();
            int mask = table.length - 1;
            int index = hashCode & mask;
            long perturb = hashCode & 0xffffffffL;
            while (true) {
                int compact = table[index];
                if (compact == EMPTY_INDEX) {
                    return null;
                }
                if (compact != DUMMY_INDEX) {
                    Bucket bucket = buckets[compact - 1];
                    if (bucket.hashCode == hashCode && bucket.hash.equals(hash)) {
                        return bucket;
                    }
                }
                perturb >>>= PERTURB_SHIFT;
                index = nextIndex(index, perturb, mask);
            }
        }

        private Entry find(Object key, ProtosNumericHashKey hash) {
            Bucket bucket = lookupBucket(hash);
            if (bucket == null) {
                return null;
            }
            Entry[] candidates = bucket.entries;
            int count = bucket.count;
            for (int index = 0; index < count; index++) {
                Entry candidate = candidates[index];
                if (ProtosIdentity.identical(key, candidate.key())) {
                    return candidate;
                }
            }
            return null;
        }

        private Bucket insertBucket(ProtosNumericHashKey hash) {
            if (indices == null) {
                indices = new int[INITIAL_INDEX_SIZE];
                buckets = new Bucket[INITIAL_INDEX_SIZE];
            } else if ((bucketsUsed + 1) * 3 >= indices.length * 2
                    || bucketsUsed == buckets.length) {
                rehash();
            }
            Bucket bucket = new Bucket(hash, hash.hashCode());
            bucket.bucketIndex = bucketsUsed;
            buckets[bucketsUsed++] = bucket;
            placeIndex(indices, bucket);
            return bucket;
        }

        private static void placeIndex(int[] table, Bucket bucket) {
            int mask = table.length - 1;
            int index = bucket.hashCode & mask;
            long perturb = bucket.hashCode & 0xffffffffL;
            while (table[index] != EMPTY_INDEX) {
                perturb >>>= PERTURB_SHIFT;
                index = nextIndex(index, perturb, mask);
            }
            table[index] = bucket.bucketIndex + 1;
        }

        private void removeBucket(Bucket bucket) {
            int[] table = indices;
            int mask = table.length - 1;
            int index = bucket.hashCode & mask;
            long perturb = bucket.hashCode & 0xffffffffL;
            int expected = bucket.bucketIndex + 1;
            while (table[index] != expected) {
                if (table[index] == EMPTY_INDEX) {
                    throw new IllegalStateException(
                            "IdentityMap hash index lost its bucket");
                }
                perturb >>>= PERTURB_SHIFT;
                index = nextIndex(index, perturb, mask);
            }
            table[index] = DUMMY_INDEX;
            buckets[bucket.bucketIndex] = null;
            bucket.bucketIndex = -1;
        }

        private void rehash() {
            int live = 0;
            for (int index = 0; index < bucketsUsed; index++) {
                if (buckets[index] != null) {
                    live++;
                }
            }
            int tableSize = INITIAL_INDEX_SIZE;
            while ((live + 1) * 3 >= tableSize) {
                tableSize <<= 1;
            }
            Bucket[] compacted = new Bucket[tableSize];
            int written = 0;
            for (int index = 0; index < bucketsUsed; index++) {
                Bucket bucket = buckets[index];
                if (bucket != null) {
                    bucket.bucketIndex = written;
                    compacted[written++] = bucket;
                }
            }
            int[] table = new int[tableSize];
            for (int index = 0; index < written; index++) {
                placeIndex(table, compacted[index]);
            }
            indices = table;
            buckets = compacted;
            bucketsUsed = written;
        }

        private List<Entry> publishKeyedSnapshot() {
            published = true;
            if (keyedSnapshotView == null) {
                keyedSnapshotView =
                        Collections.unmodifiableList(
                                Arrays.asList(entries).subList(0, size));
            }
            return keyedSnapshotView;
        }

        private List<Map.Entry<Object, Object>> publishAssociationSnapshot() {
            published = true;
            if (associationSnapshotView == null) {
                associationSnapshotView =
                        new AssociationSnapshotView(
                                Arrays.asList(entries).subList(0, size));
            }
            return associationSnapshotView;
        }

        private ProtosIndexedSnapshot publishAssociationIndexedSnapshot() {
            published = true;
            ProtosIndexedSnapshot view = associationIndexedView;
            if (view == null) {
                Object[] keysAndValues = new Object[size * 2];
                for (int index = 0; index < size; index++) {
                    Entry entry = entries[index];
                    keysAndValues[index * 2] = entry.key();
                    keysAndValues[index * 2 + 1] = entry.value();
                }
                view = ProtosIndexedSnapshot.associations(keysAndValues, size);
                associationIndexedView = view;
            }
            return view;
        }

        private IdentityMapGeneration detachedCopy() {
            IdentityMapGeneration copy =
                    new IdentityMapGeneration();

            for (int position = 0; position < size; position++) {
                Entry entry = entries[position];
                copy.append(
                        new Entry(
                                entry.key(),
                                entry.recordedIdentityHash(),
                                entry.value()));
            }

            return copy;
        }

        private IdentityMapGeneration detachedReplacing(
                Entry target,
                Object replacement) {
            IdentityMapGeneration copy =
                    new IdentityMapGeneration();
            boolean found = false;

            for (int position = 0; position < size; position++) {
                Entry entry = entries[position];
                Object copiedValue = entry.value();

                if (entry == target) {
                    copiedValue = replacement;
                    found = true;
                }

                copy.append(
                        new Entry(
                                entry.key(),
                                entry.recordedIdentityHash(),
                                copiedValue));
            }

            if (!found) {
                throw new IllegalStateException(
                        "foreign IdentityMap entry");
            }

            return copy;
        }

        private IdentityMapGeneration detachedWithout(
                Entry target) {
            IdentityMapGeneration copy =
                    new IdentityMapGeneration();
            boolean found = false;

            for (int position = 0; position < size; position++) {
                Entry entry = entries[position];
                if (entry == target) {
                    found = true;
                    continue;
                }

                copy.append(
                        new Entry(
                                entry.key(),
                                entry.recordedIdentityHash(),
                                entry.value()));
            }

            if (!found) {
                throw new IllegalStateException(
                        "foreign IdentityMap entry");
            }

            return copy;
        }

        private Object remove(Entry entry) {
            int insertionIndex = -1;
            for (int index = 0; index < size; index++) {
                if (entries[index] == entry) {
                    insertionIndex = index;
                    break;
                }
            }
            Bucket bucket = lookupBucket(entry.recordedIdentityHash());

            if (insertionIndex < 0
                    || bucket == null
                    || !bucket.removeIdentical(entry)) {
                throw new IllegalStateException(
                        "foreign IdentityMap entry");
            }

            int tail = size - insertionIndex - 1;
            if (tail > 0) {
                System.arraycopy(entries, insertionIndex + 1, entries, insertionIndex, tail);
            }
            entries[--size] = null;

            if (bucket.count == 0) {
                removeBucket(bucket);
            }

            return entry.value();
        }
    }

    private IdentityMapGeneration generation =
            new IdentityMapGeneration();

    public ProtosIdentityMapValue(Object parent) {
        super(parent);
    }

    public int keyedSize() {
        return generation.size;
    }

    public List<Entry> keyedSnapshot() {
        return generation.publishKeyedSnapshot();
    }

    public List<Entry> candidatesForRecordedIdentityHash(
            ProtosNumericHashKey hash) {
        return generation.candidates(hash);
    }

    /**
     * The entry whose key is identical to {@code key}, located through the
     * current generation's bucket index with the caller's already computed
     * identity hash; null when absent.
     */
    public Entry findIdentical(Object key, ProtosNumericHashKey hash) {
        return generation.find(key, Objects.requireNonNull(hash, "hash"));
    }

    public List<Map.Entry<Object, Object>> associationSnapshot() {
        return generation.publishAssociationSnapshot();
    }

    /**
     * The insertion-ordered associations of the current generation as one
     * indexed snapshot. Publishing it freezes the generation exactly like the
     * List snapshots, so it is independent of later mutation.
     */
    public ProtosIndexedSnapshot associationIndexedSnapshot() {
        return generation.publishAssociationIndexedSnapshot();
    }

    public void append(
            Object key,
            ProtosNumericHashKey hash,
            Object value) {
        if (generation.published) {
            generation = generation.detachedCopy();
        }

        generation.append(
                new Entry(key, hash, value));
    }

    public void replaceValue(
            Entry entry,
            Object value) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(value, "value");

        if (generation.published) {
            generation =
                    generation.detachedReplacing(
                            entry,
                            value);
            return;
        }

        entry.value(value);
    }

    public Object remove(Entry entry) {
        Objects.requireNonNull(entry, "entry");

        if (generation.published) {
            Object removed = entry.value();
            generation =
                    generation.detachedWithout(entry);
            return removed;
        }

        return generation.remove(entry);
    }
}
