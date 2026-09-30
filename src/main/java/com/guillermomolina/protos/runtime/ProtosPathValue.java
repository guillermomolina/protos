/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, authority-free Core Path value (D169): one ordered sequence of normal component
 * Strings. The empty sequence is the interpreting Filesystem's configured base.
 *
 * <p>Structural equality and hash depend only on that sequence; the delegation prototype and any
 * Filesystem are not part of it. Components are opaque Strings and never acquire host separator,
 * drive, or UNC structure.
 */
@ExportLibrary(InteropLibrary.class)
public final class ProtosPathValue implements ProtosRepresentedValue {
    private final ProtosObjectValue prototype;
    private final List<String> components;

    public ProtosPathValue(ProtosObjectValue prototype, List<String> components) {
        this.prototype = Objects.requireNonNull(prototype);
        this.components = List.copyOf(components);
    }

    public List<String> components() {
        return components;
    }

    public ProtosPathValue child(String name) {
        ArrayList<String> extended = new ArrayList<>(components);
        extended.add(Objects.requireNonNull(name, "name"));
        return new ProtosPathValue(prototype, extended);
    }

    public boolean structurallyEquals(ProtosPathValue other) {
        return other != null && components.equals(other.components);
    }

    public int structuralHash() {
        return components.hashCode();
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude p) {
        return prototype;
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Object";
    }
}
