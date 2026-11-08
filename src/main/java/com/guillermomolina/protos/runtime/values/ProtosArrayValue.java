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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@ExportLibrary(InteropLibrary.class)
public final class ProtosArrayValue extends ProtosObjectValue {
    /*
     * A published generation must never be mutated again. Snapshot publication
     * therefore stays O(1): the first snapshot creates only a read-only view of
     * the current backing, while the first later replacement detaches the Array
     * to a fresh generation before writing. Old generations remain reachable
     * exactly as long as their shallow snapshots do.
     */
    private static final class IndexedGeneration {
        private ArrayList<Object> elements;
        private long[] longElements;
        private List<Object> snapshotView;
        private boolean published;

        private IndexedGeneration(ArrayList<Object> values) {
            Objects.requireNonNull(values, "values");

            boolean allLong = !values.isEmpty();
            for (Object value : values) {
                if (!(value instanceof Long)) {
                    allLong = false;
                    break;
                }
            }

            if (allLong) {
                longElements = new long[values.size()];
                for (int index = 0; index < values.size(); index++) {
                    longElements[index] =
                            ((Long) values.get(index)).longValue();
                }
            } else {
                elements = values;
            }
        }

        private IndexedGeneration(long[] values) {
            longElements = Objects.requireNonNull(values, "values");
        }

        private int size() {
            return longElements != null
                    ? longElements.length
                    : elements.size();
        }

        private Object get(int index) {
            return longElements != null
                    ? Long.valueOf(longElements[index])
                    : elements.get(index);
        }

        private long getLong(int index) {
            return longElements[index];
        }

        private void put(int index, Object value) {
            if (longElements != null) {
                if (value instanceof Long number) {
                    longElements[index] = number.longValue();
                    return;
                }

                ArrayList<Object> widened =
                        new ArrayList<>(longElements.length);
                for (long element : longElements) {
                    widened.add(Long.valueOf(element));
                }

                elements = widened;
                longElements = null;
            }

            elements.set(index, value);
        }

        private void putLong(int index, long value) {
            if (longElements != null) {
                longElements[index] = value;
            } else {
                elements.set(index, Long.valueOf(value));
            }
        }

        private List<Object> publishSnapshot() {
            published = true;

            if (snapshotView == null) {
                if (longElements == null) {
                    snapshotView = Collections.unmodifiableList(elements);
                } else {
                    IndexedGeneration captured = this;
                    snapshotView = Collections.unmodifiableList(
                            new AbstractList<Object>() {
                                @Override
                                public Object get(int index) {
                                    return captured.get(index);
                                }

                                @Override
                                public int size() {
                                    return captured.size();
                                }
                            });
                }
            }

            return snapshotView;
        }

        private IndexedGeneration detachedCopy() {
            return longElements != null
                    ? new IndexedGeneration(longElements.clone())
                    : new IndexedGeneration(new ArrayList<>(elements));
        }
    }

    private IndexedGeneration indexedGeneration;

    public ProtosArrayValue(Object parent, List<?> elements) {
        super(parent);
        Objects.requireNonNull(elements, "elements");

        int size = elements.size();
        ArrayList<Object> ownedElements = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            Object element = elements.get(index);
            ownedElements.add(Objects.requireNonNull(element, "element"));
        }
        this.indexedGeneration = new IndexedGeneration(ownedElements);
    }

    /*
     * I115: GraalPy LongSequenceStorage construction.
     * Each Array gets its own primitive backing.
     */
    public ProtosArrayValue(Object parent, long[] elements) {
        super(parent);
        this.indexedGeneration =
                new IndexedGeneration(
                        Objects.requireNonNull(elements, "elements").clone());
    }

    public int indexedSize() {
        return indexedGeneration.size();
    }

    public boolean hasLongStorageForRuntime() {
        return indexedGeneration.longElements != null;
    }

    public Object indexedAt(int index) {
        return indexedGeneration.get(requireExistingIndex(index));
    }

    public long indexedLongAt(int index) {
        return indexedGeneration.getLong(requireExistingIndex(index));
    }

    public Object indexedPut(int index, Object value) {
        Objects.requireNonNull(value, "value");
        if (isFrozen()) {
            throw new IllegalStateException("array is frozen");
        }

        int existingIndex = requireExistingIndex(index);
        writableIndexedGeneration().put(existingIndex, value);
        return value;
    }

    public long indexedLongPut(int index, long value) {
        if (isFrozen()) {
            throw new IllegalStateException("array is frozen");
        }

        int existingIndex = requireExistingIndex(index);
        writableIndexedGeneration().putLong(existingIndex, value);
        return value;
    }

    public List<Object> indexedSnapshot() {
        return indexedGeneration.publishSnapshot();
    }

    private IndexedGeneration writableIndexedGeneration() {
        if (indexedGeneration.published) {
            indexedGeneration = indexedGeneration.detachedCopy();
        }
        return indexedGeneration;
    }

    private int requireExistingIndex(int index) {
        if (index < 0 || index >= indexedGeneration.size()) {
            throw new IndexOutOfBoundsException("array index out of bounds: " + index);
        }
        return index;
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    long getArraySize() {
        return indexedGeneration.size();
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        if (index < 0 || index >= indexedGeneration.size()) {
            return false;
        }
        return InteropLibrary.isValidValue(indexedAt((int) index));
    }

    @ExportMessage
    Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(index)) {
            throw InvalidArrayIndexException.create(index);
        }
        return indexedAt((int) index);
    }

    /*
     * InteropLibrary otherwise derives an iterator automatically from array
     * elements. PLAT013/I026-D5 deliberately exposes only indexed read
     * structure, not a second tooling iteration protocol.
     */
    @ExportMessage
    boolean hasIterator() {
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    Object getIterator() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @Override
    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Array";
    }
}
