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
        /*
         * Exactly one of the four backings is non-null; it has the Array's
         * size. A primitive backing holds only values of its guest family
         * (signed-64 Integer, Float as Double, canonical Boolean); any other
         * value promotes the generation to Object[] storage.
         */
        private Object[] elements;
        private long[] longElements;
        private double[] doubleElements;
        private boolean[] booleanElements;
        private List<Object> snapshotView;
        private ProtosIndexedSnapshot indexedView;
        private boolean published;

        private IndexedGeneration(Object[] values) {
            Objects.requireNonNull(values, "values");

            if (values.length == 0) {
                elements = values;
                return;
            }

            Object first = values[0];
            Class<?> family = first instanceof Long || first instanceof Double
                    || first instanceof Boolean ? first.getClass() : null;
            for (int index = 1; family != null && index < values.length; index++) {
                if (values[index] == null || values[index].getClass() != family) {
                    family = null;
                }
            }

            if (family == Long.class) {
                longElements = new long[values.length];
                for (int index = 0; index < values.length; index++) {
                    longElements[index] = ((Long) values[index]).longValue();
                }
            } else if (family == Double.class) {
                doubleElements = new double[values.length];
                for (int index = 0; index < values.length; index++) {
                    doubleElements[index] = ((Double) values[index]).doubleValue();
                }
            } else if (family == Boolean.class) {
                booleanElements = new boolean[values.length];
                for (int index = 0; index < values.length; index++) {
                    booleanElements[index] = ((Boolean) values[index]).booleanValue();
                }
            } else {
                elements = values;
            }
        }

        private IndexedGeneration(long[] values) {
            longElements = Objects.requireNonNull(values, "values");
        }

        private IndexedGeneration(double[] values) {
            doubleElements = Objects.requireNonNull(values, "values");
        }

        private IndexedGeneration(boolean[] values) {
            booleanElements = Objects.requireNonNull(values, "values");
        }

        private int size() {
            if (longElements != null) {
                return longElements.length;
            }
            if (doubleElements != null) {
                return doubleElements.length;
            }
            if (booleanElements != null) {
                return booleanElements.length;
            }
            return elements.length;
        }

        private Object get(int index) {
            if (longElements != null) {
                return Long.valueOf(longElements[index]);
            }
            if (doubleElements != null) {
                return Double.valueOf(doubleElements[index]);
            }
            if (booleanElements != null) {
                return Boolean.valueOf(booleanElements[index]);
            }
            return elements[index];
        }

        private long getLong(int index) {
            return longElements[index];
        }

        private double getDouble(int index) {
            return doubleElements[index];
        }

        private boolean getBoolean(int index) {
            return booleanElements[index];
        }

        private void put(int index, Object value) {
            if (longElements != null) {
                if (value instanceof Long number) {
                    longElements[index] = number.longValue();
                    return;
                }
            } else if (doubleElements != null) {
                if (value instanceof Double number) {
                    doubleElements[index] = number.doubleValue();
                    return;
                }
            } else if (booleanElements != null) {
                if (value instanceof Boolean flag) {
                    booleanElements[index] = flag.booleanValue();
                    return;
                }
            }
            if (elements == null) {
                promoteToObjects();
            }
            elements[index] = value;
        }

        private void putLong(int index, long value) {
            if (longElements != null) {
                longElements[index] = value;
            } else {
                put(index, Long.valueOf(value));
            }
        }

        private void putDouble(int index, double value) {
            if (doubleElements != null) {
                doubleElements[index] = value;
            } else {
                put(index, Double.valueOf(value));
            }
        }

        private void putBoolean(int index, boolean value) {
            if (booleanElements != null) {
                booleanElements[index] = value;
            } else {
                put(index, Boolean.valueOf(value));
            }
        }

        /*
         * Replaces the primitive backing with Object[] storage holding the
         * exact guest value of every existing element. Runs only when a
         * primitive generation receives a value outside its family.
         */
        @TruffleBoundary
        private void promoteToObjects() {
            int size = size();
            Object[] widened = new Object[size];
            for (int position = 0; position < size; position++) {
                widened[position] = get(position);
            }
            elements = widened;
            longElements = null;
            doubleElements = null;
            booleanElements = null;
        }

        @TruffleBoundary
        private List<Object> publishSnapshot() {
            published = true;

            if (snapshotView == null) {
                IndexedGeneration captured = this;
                snapshotView = Collections.unmodifiableList(
                        new AbstractList<Object>() {
                            @Override
                            public Object get(int index) {
                                Objects.checkIndex(index, captured.size());
                                return captured.get(index);
                            }

                            @Override
                            public int size() {
                                return captured.size();
                            }
                        });
            }

            return snapshotView;
        }

        private ProtosIndexedSnapshot publishIndexedSnapshot() {
            published = true;

            ProtosIndexedSnapshot view = indexedView;
            if (view == null) {
                if (longElements != null) {
                    view = ProtosIndexedSnapshot.longElements(longElements);
                } else if (doubleElements != null) {
                    view = ProtosIndexedSnapshot.doubleElements(doubleElements);
                } else if (booleanElements != null) {
                    view = ProtosIndexedSnapshot.booleanElements(booleanElements);
                } else {
                    view = ProtosIndexedSnapshot.elements(elements, elements.length);
                }
                indexedView = view;
            }
            return view;
        }

        /*
         * Runs only on the first write after a publication, so the clone
         * stays out of the ordinary indexed read/write path.
         */
        @TruffleBoundary
        private IndexedGeneration detachedCopy() {
            if (longElements != null) {
                return new IndexedGeneration(longElements.clone());
            }
            if (doubleElements != null) {
                return new IndexedGeneration(doubleElements.clone());
            }
            if (booleanElements != null) {
                return new IndexedGeneration(booleanElements.clone());
            }
            return new IndexedGeneration(elements.clone());
        }
    }

    private IndexedGeneration indexedGeneration;

    public ProtosArrayValue(Object parent, List<?> elements) {
        super(parent);
        Objects.requireNonNull(elements, "elements");

        int size = elements.size();
        Object[] ownedElements = new Object[size];
        for (int index = 0; index < size; index++) {
            Object element = elements.get(index);
            ownedElements[index] = Objects.requireNonNull(element, "element");
        }
        this.indexedGeneration = new IndexedGeneration(ownedElements);
    }

    /*
     * I115: specialized long-storage construction.
     * Each Array gets its own primitive backing.
     */
    public ProtosArrayValue(Object parent, long[] elements) {
        super(parent);
        this.indexedGeneration =
                new IndexedGeneration(
                        Objects.requireNonNull(elements, "elements").clone());
    }

    /* Specialized Float-storage construction with its own primitive backing. */
    public ProtosArrayValue(Object parent, double[] elements) {
        super(parent);
        this.indexedGeneration =
                new IndexedGeneration(
                        Objects.requireNonNull(elements, "elements").clone());
    }

    /* Specialized Boolean-storage construction with its own primitive backing. */
    public ProtosArrayValue(Object parent, boolean[] elements) {
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

    public boolean hasDoubleStorageForRuntime() {
        return indexedGeneration.doubleElements != null;
    }

    public boolean hasBooleanStorageForRuntime() {
        return indexedGeneration.booleanElements != null;
    }

    public Object indexedAt(int index) {
        return indexedAtNormalized(requireExistingIndex(index));
    }

    public long indexedLongAt(int index) {
        return indexedLongAtNormalized(requireExistingIndex(index));
    }

    public double indexedDoubleAt(int index) {
        return indexedDoubleAtNormalized(requireExistingIndex(index));
    }

    public boolean indexedBooleanAt(int index) {
        return indexedBooleanAtNormalized(requireExistingIndex(index));
    }

    public Object indexedPut(int index, Object value) {
        Objects.requireNonNull(value, "value");
        if (isFrozen()) {
            throw new IllegalStateException("array is frozen");
        }

        return indexedPutNormalized(requireExistingIndex(index), value);
    }

    public long indexedLongPut(int index, long value) {
        if (isFrozen()) {
            throw new IllegalStateException("array is frozen");
        }

        return indexedLongPutNormalized(requireExistingIndex(index), value);
    }

    public double indexedDoublePut(int index, double value) {
        if (isFrozen()) {
            throw new IllegalStateException("array is frozen");
        }

        return indexedDoublePutNormalized(requireExistingIndex(index), value);
    }

    public boolean indexedBooleanPut(int index, boolean value) {
        if (isFrozen()) {
            throw new IllegalStateException("array is frozen");
        }

        return indexedBooleanPutNormalized(requireExistingIndex(index), value);
    }

    /*
     * Normalized storage access. The caller has already validated the
     * complete guest index against the current size (and, for writes, the
     * frozen state), so these read or write the current generation
     * directly. Writes keep copy-on-write: a published generation is
     * detached before it is modified, so earlier snapshots never change.
     */
    public Object indexedAtNormalized(int index) {
        return indexedGeneration.get(index);
    }

    /* Requires primitive long storage. */
    public long indexedLongAtNormalized(int index) {
        return indexedGeneration.getLong(index);
    }

    /* Requires primitive Float storage. */
    public double indexedDoubleAtNormalized(int index) {
        return indexedGeneration.getDouble(index);
    }

    /* Requires primitive Boolean storage. */
    public boolean indexedBooleanAtNormalized(int index) {
        return indexedGeneration.getBoolean(index);
    }

    public Object indexedPutNormalized(int index, Object value) {
        Objects.requireNonNull(value, "value");
        writableIndexedGeneration().put(index, value);
        return value;
    }

    public long indexedLongPutNormalized(int index, long value) {
        writableIndexedGeneration().putLong(index, value);
        return value;
    }

    public double indexedDoublePutNormalized(int index, double value) {
        writableIndexedGeneration().putDouble(index, value);
        return value;
    }

    public boolean indexedBooleanPutNormalized(int index, boolean value) {
        writableIndexedGeneration().putBoolean(index, value);
        return value;
    }

    public List<Object> indexedSnapshot() {
        return indexedGeneration.publishSnapshot();
    }

    /**
     * The current generation published as one indexed snapshot: the
     * generation's own backing, read by index, never written again, so it is
     * independent of later writes (which detach a fresh generation first).
     */
    public ProtosIndexedSnapshot indexedGenerationSnapshot() {
        return indexedGeneration.publishIndexedSnapshot();
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
