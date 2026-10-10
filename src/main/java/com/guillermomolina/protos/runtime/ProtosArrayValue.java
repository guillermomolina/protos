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
        private final ArrayList<Object> elements;
        private List<Object> snapshotView;
        private boolean published;

        private IndexedGeneration(ArrayList<Object> elements) {
            this.elements = Objects.requireNonNull(elements, "elements");
        }

        private List<Object> publishSnapshot() {
            published = true;
            if (snapshotView == null) {
                snapshotView = Collections.unmodifiableList(elements);
            }
            return snapshotView;
        }

        private IndexedGeneration detachedCopy() {
            return new IndexedGeneration(new ArrayList<>(elements));
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

    public int indexedSize() {
        return indexedGeneration.elements.size();
    }

    public Object indexedAt(int index) {
        return indexedGeneration.elements.get(requireExistingIndex(index));
    }

    public Object indexedPut(int index, Object value) {
        Objects.requireNonNull(value, "value");
        if (isFrozen()) {
            throw new IllegalStateException("array is frozen");
        }

        int existingIndex = requireExistingIndex(index);
        writableIndexedGeneration().elements.set(existingIndex, value);
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
        if (index < 0 || index >= indexedGeneration.elements.size()) {
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
        return indexedGeneration.elements.size();
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        if (index < 0 || index >= indexedGeneration.elements.size()) {
            return false;
        }
        return InteropLibrary.isValidValue(
                indexedGeneration.elements.get((int) index));
    }

    @ExportMessage
    Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(index)) {
            throw InvalidArrayIndexException.create(index);
        }
        return indexedGeneration.elements.get((int) index);
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
