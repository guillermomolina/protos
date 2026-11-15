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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

    private static final class Bucket {
        private final List<Entry> entries = new ArrayList<>();
        private final List<Entry> view =
                Collections.unmodifiableList(entries);
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
        private final Map<ProtosNumericHashKey, Bucket> entriesByIdentityHash =
                new HashMap<>();

        private List<Entry> keyedSnapshotView;
        private List<Map.Entry<Object, Object>> associationSnapshotView;
        private ProtosIndexedSnapshot associationIndexedView;
        private boolean published;

        private void append(Entry entry) {
            if (size == entries.length) {
                entries = Arrays.copyOf(entries, Math.max(4, size * 2));
            }
            entries[size++] = entry;
            entriesByIdentityHash
                    .computeIfAbsent(
                            entry.recordedIdentityHash(),
                            ignored -> new Bucket())
                    .entries
                    .add(entry);
        }

        private List<Entry> candidates(ProtosNumericHashKey hash) {
            Bucket bucket =
                    entriesByIdentityHash.get(
                            Objects.requireNonNull(hash, "hash"));
            return bucket == null ? List.of() : bucket.view;
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
            Bucket bucket =
                    entriesByIdentityHash.get(
                            entry.recordedIdentityHash());

            if (insertionIndex < 0
                    || bucket == null
                    || !bucket.entries.contains(entry)) {
                throw new IllegalStateException(
                        "foreign IdentityMap entry");
            }

            int tail = size - insertionIndex - 1;
            if (tail > 0) {
                System.arraycopy(entries, insertionIndex + 1, entries, insertionIndex, tail);
            }
            entries[--size] = null;
            bucket.entries.remove(entry);

            if (bucket.entries.isEmpty()) {
                entriesByIdentityHash.remove(
                        entry.recordedIdentityHash());
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
