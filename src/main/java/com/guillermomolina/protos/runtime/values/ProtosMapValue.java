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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Insertion-ordered keyed storage of a Map.
 *
 * <p>The physical layout is an open-addressing index of compact indices over
 * dense arrays, with lazy deletion and compaction on resize: {@code order}
 * keeps the physical entries in insertion order (removed positions become
 * {@code null} until compaction), and {@code indices} maps an exact recorded
 * hash to its {@link Candidates} in {@code buckets}. Each physical
 * {@link Entry} records its own position and bucket, so finding or removing
 * an already selected entry is an identity test, never {@code equals}.
 *
 * <p>The storage owns no key semantics: Protos {@code hash} and {@code ==}
 * selection is performed by the structured and standard Map protocols before
 * any physical operation receives an Entry.
 */
public final class ProtosMapValue extends ProtosObjectValue {
    private static final Entry[] NO_ENTRIES = new Entry[0];
    private static final int EMPTY_INDEX = 0;
    private static final int DUMMY_INDEX = -1;
    private static final int INITIAL_INDEX_SIZE = 8;
    private static final int INITIAL_ORDER_SIZE = 4;
    private static final int PERTURB_SHIFT = 5;

    public static final class Entry {
        private final Object key;
        private final ProtosNumericHashKey hash;
        private Object value;
        /* Physical position in the owning order array, or -1 once removed. */
        private int position = -1;
        private Candidates candidates;

        Entry(Object k, ProtosNumericHashKey h, Object v) {
            key = Objects.requireNonNull(k);
            hash = Objects.requireNonNull(h);
            value = Objects.requireNonNull(v);
        }

        public Object key() {
            return key;
        }

        public ProtosNumericHashKey recordedHash() {
            return hash;
        }

        public Object value() {
            return value;
        }

        void value(Object v) {
            value = Objects.requireNonNull(v);
        }
    }

    /**
     * The live, insertion-ordered physical entries sharing one exact recorded
     * hash. The same instance is returned for as long as its hash has entries.
     */
    public static final class Candidates {
        private static final Candidates NONE = new Candidates(null, 0);

        private final ProtosNumericHashKey hash;
        private final int hashCode;
        private Entry[] entries = NO_ENTRIES;
        private int size;
        /* Position in the owning buckets array, or -1 once detached. */
        private int bucketIndex = -1;

        private Candidates(ProtosNumericHashKey hash, int hashCode) {
            this.hash = hash;
            this.hashCode = hashCode;
        }

        public int size() {
            return size;
        }

        public boolean isEmpty() {
            return size == 0;
        }

        public Entry get(int index) {
            Objects.checkIndex(index, size);
            return entries[index];
        }

        private void add(Entry entry) {
            if (size == entries.length) {
                entries = Arrays.copyOf(entries, Math.max(2, size * 2));
            }
            entries[size++] = entry;
        }

        private boolean removeIdentical(Entry entry) {
            for (int index = 0; index < size; index++) {
                if (entries[index] == entry) {
                    int tail = size - index - 1;
                    if (tail > 0) {
                        System.arraycopy(entries, index + 1, entries, index, tail);
                    }
                    entries[--size] = null;
                    return true;
                }
            }
            return false;
        }
    }

    private Entry[] order = NO_ENTRIES;
    private int orderUsed;
    private int size;

    private int[] indices;
    private Candidates[] buckets;
    private int bucketsUsed;

    private int comparisonDepth;

    public ProtosMapValue(Object parent) {
        super(parent);
    }

    public int keyedSize() {
        return size;
    }

    /** Tooling and transfer snapshot of the physical entries in insertion order. */
    @TruffleBoundary
    public List<Entry> keyedSnapshot() {
        ArrayList<Entry> snapshot = new ArrayList<>(size);
        for (int index = 0; index < orderUsed; index++) {
            Entry entry = order[index];
            if (entry != null) {
                snapshot.add(entry);
            }
        }
        return List.copyOf(snapshot);
    }

    public Candidates candidatesForRecordedHash(ProtosNumericHashKey hash) {
        Candidates candidates = lookupBucket(Objects.requireNonNull(hash));
        return candidates == null ? Candidates.NONE : candidates;
    }

    /** Tooling and transfer snapshot of the associations in insertion order. */
    @TruffleBoundary
    public List<Map.Entry<Object, Object>> associationSnapshot() {
        ArrayList<Map.Entry<Object, Object>> snapshot = new ArrayList<>(size);
        for (int index = 0; index < orderUsed; index++) {
            Entry entry = order[index];
            if (entry != null) {
                snapshot.add(Map.entry(entry.key(), entry.value()));
            }
        }
        return List.copyOf(snapshot);
    }

    /**
     * The representative keys and their current values, in insertion order,
     * as one indexed generation independent of later mutation.
     */
    public ProtosIndexedSnapshot associationIndexedSnapshot() {
        Object[] keysAndValues = new Object[size * 2];
        int written = 0;
        for (int index = 0; index < orderUsed; index++) {
            Entry entry = order[index];
            if (entry != null) {
                keysAndValues[written++] = entry.key();
                keysAndValues[written++] = entry.value();
            }
        }
        return ProtosIndexedSnapshot.associations(keysAndValues, size);
    }

    public boolean comparisonActive() {
        return comparisonDepth != 0;
    }

    public void enterComparison() {
        comparisonDepth++;
    }

    public void leaveComparison() {
        if (comparisonDepth <= 0) throw new IllegalStateException("unbalanced Map comparison scope");
        comparisonDepth--;
    }

    public void append(Object k, ProtosNumericHashKey h, Object v) {
        Entry entry = new Entry(k, h, v);
        Candidates candidates = lookupBucket(entry.recordedHash());
        if (candidates == null) {
            candidates = insertBucket(entry.recordedHash());
        }
        appendToOrder(entry);
        candidates.add(entry);
        entry.candidates = candidates;
        size++;
    }

    public void replaceValue(Entry e, Object v) {
        e.value(v);
    }

    /** Removes the physical entry {@code e}, already selected by the Map protocol. */
    public Object remove(Entry e) {
        Objects.requireNonNull(e);
        int position = e.position;
        Candidates candidates = e.candidates;
        if (position < 0
                || position >= orderUsed
                || order[position] != e
                || candidates == null
                || candidates.bucketIndex < 0
                || buckets[candidates.bucketIndex] != candidates
                || !candidates.removeIdentical(e)) {
            throw new IllegalStateException("foreign Map entry");
        }
        order[position] = null;
        e.position = -1;
        size--;
        while (orderUsed > 0 && order[orderUsed - 1] == null) {
            orderUsed--;
        }
        if (candidates.isEmpty()) {
            removeBucket(candidates);
        }
        return e.value();
    }

    // ---- insertion order ------------------------------------------------------------

    private void appendToOrder(Entry entry) {
        if (orderUsed == order.length) {
            if (size < orderUsed && size <= orderUsed / 2) {
                compactOrder();
            } else {
                order = Arrays.copyOf(order, Math.max(INITIAL_ORDER_SIZE, order.length * 2));
            }
        }
        entry.position = orderUsed;
        order[orderUsed++] = entry;
    }

    private void compactOrder() {
        int written = 0;
        for (int index = 0; index < orderUsed; index++) {
            Entry entry = order[index];
            if (entry != null) {
                entry.position = written;
                order[written++] = entry;
            }
        }
        Arrays.fill(order, written, orderUsed, null);
        orderUsed = written;
    }

    // ---- exact recorded-hash index --------------------------------------------------

    private static int nextIndex(int index, long perturb, int mask) {
        return (int) (((5L * index) + perturb + 1L) & mask);
    }

    private Candidates lookupBucket(ProtosNumericHashKey hash) {
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
                Candidates candidates = buckets[compact - 1];
                if (candidates.hashCode == hashCode && candidates.hash.equals(hash)) {
                    return candidates;
                }
            }
            perturb >>>= PERTURB_SHIFT;
            index = nextIndex(index, perturb, mask);
        }
    }

    private Candidates insertBucket(ProtosNumericHashKey hash) {
        if (indices == null) {
            indices = new int[INITIAL_INDEX_SIZE];
            buckets = new Candidates[INITIAL_INDEX_SIZE];
        } else if ((bucketsUsed + 1) * 3 >= indices.length * 2 || bucketsUsed == buckets.length) {
            rehash();
        }
        Candidates candidates = new Candidates(hash, hash.hashCode());
        candidates.bucketIndex = bucketsUsed;
        buckets[bucketsUsed++] = candidates;
        placeIndex(indices, candidates);
        return candidates;
    }

    private void placeIndex(int[] table, Candidates candidates) {
        int mask = table.length - 1;
        int index = candidates.hashCode & mask;
        long perturb = candidates.hashCode & 0xffffffffL;
        while (table[index] != EMPTY_INDEX) {
            perturb >>>= PERTURB_SHIFT;
            index = nextIndex(index, perturb, mask);
        }
        table[index] = candidates.bucketIndex + 1;
    }

    private void removeBucket(Candidates candidates) {
        int[] table = indices;
        int mask = table.length - 1;
        int index = candidates.hashCode & mask;
        long perturb = candidates.hashCode & 0xffffffffL;
        int expected = candidates.bucketIndex + 1;
        while (table[index] != expected) {
            if (table[index] == EMPTY_INDEX) {
                throw new IllegalStateException("Map hash index lost its bucket");
            }
            perturb >>>= PERTURB_SHIFT;
            index = nextIndex(index, perturb, mask);
        }
        table[index] = DUMMY_INDEX;
        buckets[candidates.bucketIndex] = null;
        candidates.bucketIndex = -1;
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
        Candidates[] compacted = new Candidates[tableSize];
        int written = 0;
        for (int index = 0; index < bucketsUsed; index++) {
            Candidates candidates = buckets[index];
            if (candidates != null) {
                candidates.bucketIndex = written;
                compacted[written++] = candidates;
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
}
