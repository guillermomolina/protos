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

import java.util.Objects;

/**
 * An immutable, indexed generation of values read by internal structured
 * operations without a Java collection interface.
 *
 * <p>The storage is a plain array whose observable prefix has a known length:
 * either one {@code Object} per position, the specialized {@code long[]} of a
 * primitive Array generation, or interleaved key/value pairs for association
 * snapshots. The publisher guarantees the shared range is never written
 * again, so a snapshot is independent of later mutation of its source and
 * reading a position never copies.
 */
public final class ProtosIndexedSnapshot {
    private static final Object[] NO_OBJECTS = new Object[0];
    private static final ProtosIndexedSnapshot EMPTY_ELEMENTS =
            new ProtosIndexedSnapshot(NO_OBJECTS, null, 0, false);
    private static final ProtosIndexedSnapshot EMPTY_ASSOCIATIONS =
            new ProtosIndexedSnapshot(NO_OBJECTS, null, 0, true);

    private final Object[] objects;
    private final long[] longs;
    private final int length;
    private final boolean associations;

    private ProtosIndexedSnapshot(
            Object[] objects, long[] longs, int length, boolean associations) {
        this.objects = objects;
        this.longs = longs;
        this.length = length;
        this.associations = associations;
    }

    /** An element snapshot adopting {@code values[0, length)}, never written again. */
    public static ProtosIndexedSnapshot elements(Object[] values, int length) {
        Objects.requireNonNull(values, "values");
        Objects.checkFromToIndex(0, length, values.length);
        return length == 0
                ? EMPTY_ELEMENTS
                : new ProtosIndexedSnapshot(values, null, length, false);
    }

    /** An element snapshot adopting a primitive generation never written again. */
    public static ProtosIndexedSnapshot longElements(long[] values) {
        Objects.requireNonNull(values, "values");
        return values.length == 0
                ? EMPTY_ELEMENTS
                : new ProtosIndexedSnapshot(null, values, values.length, false);
    }

    /**
     * An association snapshot adopting {@code length} interleaved key/value
     * pairs of {@code keysAndValues}, never written again.
     */
    public static ProtosIndexedSnapshot associations(Object[] keysAndValues, int length) {
        Objects.requireNonNull(keysAndValues, "keysAndValues");
        Objects.checkFromToIndex(0, length, keysAndValues.length / 2);
        return length == 0
                ? EMPTY_ASSOCIATIONS
                : new ProtosIndexedSnapshot(keysAndValues, null, length, true);
    }

    /** The number of positions of this generation. */
    public int length() {
        return length;
    }

    /** The element at {@code index} of an element snapshot. */
    public Object at(int index) {
        if (associations) {
            throw new IllegalStateException("association snapshot has no single elements");
        }
        Objects.checkIndex(index, length);
        long[] primitive = longs;
        return primitive != null ? Long.valueOf(primitive[index]) : objects[index];
    }

    /** The key of association {@code index}. */
    public Object keyAt(int index) {
        requireAssociations();
        Objects.checkIndex(index, length);
        return objects[index * 2];
    }

    /** The value of association {@code index}. */
    public Object valueAt(int index) {
        requireAssociations();
        Objects.checkIndex(index, length);
        return objects[index * 2 + 1];
    }

    private void requireAssociations() {
        if (!associations) {
            throw new IllegalStateException("element snapshot has no associations");
        }
    }
}
