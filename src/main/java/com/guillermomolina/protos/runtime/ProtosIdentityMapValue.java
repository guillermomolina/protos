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

import java.math.BigInteger;
import java.util.AbstractList;
import java.util.ArrayList;
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
        private final BigInteger hash;
        private Object value;

        Entry(Object key, BigInteger hash, Object value) {
            this.key = Objects.requireNonNull(key, "key");
            this.hash = Objects.requireNonNull(hash, "hash");
            this.value = Objects.requireNonNull(value, "value");
        }

        public Object key() {
            return key;
        }

        public BigInteger recordedIdentityHash() {
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
        private final List<Entry> entries = new ArrayList<>();
        private final Map<BigInteger, Bucket> entriesByIdentityHash =
                new HashMap<>();

        private List<Entry> keyedSnapshotView;
        private List<Map.Entry<Object, Object>> associationSnapshotView;
        private boolean published;

        private void append(Entry entry) {
            entries.add(entry);
            entriesByIdentityHash
                    .computeIfAbsent(
                            entry.recordedIdentityHash(),
                            ignored -> new Bucket())
                    .entries
                    .add(entry);
        }

        private List<Entry> candidates(BigInteger hash) {
            Bucket bucket =
                    entriesByIdentityHash.get(
                            Objects.requireNonNull(hash, "hash"));
            return bucket == null ? List.of() : bucket.view;
        }

        private List<Entry> publishKeyedSnapshot() {
            published = true;
            if (keyedSnapshotView == null) {
                keyedSnapshotView =
                        Collections.unmodifiableList(entries);
            }
            return keyedSnapshotView;
        }

        private List<Map.Entry<Object, Object>> publishAssociationSnapshot() {
            published = true;
            if (associationSnapshotView == null) {
                associationSnapshotView =
                        new AssociationSnapshotView(entries);
            }
            return associationSnapshotView;
        }

        private IdentityMapGeneration detachedCopy() {
            IdentityMapGeneration copy =
                    new IdentityMapGeneration();

            for (Entry entry : entries) {
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

            for (Entry entry : entries) {
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

            for (Entry entry : entries) {
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
            int insertionIndex = entries.indexOf(entry);
            Bucket bucket =
                    entriesByIdentityHash.get(
                            entry.recordedIdentityHash());

            if (insertionIndex < 0
                    || bucket == null
                    || !bucket.entries.contains(entry)) {
                throw new IllegalStateException(
                        "foreign IdentityMap entry");
            }

            entries.remove(insertionIndex);
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
        return generation.entries.size();
    }

    public List<Entry> keyedSnapshot() {
        return generation.publishKeyedSnapshot();
    }

    public List<Entry> candidatesForRecordedIdentityHash(
            BigInteger hash) {
        return generation.candidates(hash);
    }

    public List<Map.Entry<Object, Object>> associationSnapshot() {
        return generation.publishAssociationSnapshot();
    }

    public void append(
            Object key,
            BigInteger hash,
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
