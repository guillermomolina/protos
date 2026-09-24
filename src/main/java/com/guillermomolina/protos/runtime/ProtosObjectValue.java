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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@ExportLibrary(InteropLibrary.class)
public class ProtosObjectValue implements TruffleObject {
    public enum MutationState {
        OPEN,
        CLOSED,
        FROZEN
    }

    private static final ProtosObjectValue ROOT = new ProtosObjectValue();

    private final Object parent;
    /**
     * The single authority for this object's lexical local-slot bindings
     * (PLAT036 Candidate D). Installed at construction; every local-slot
     * operation below is routed through it rather than touching a storage
     * field directly, so a subclass such as {@link ProtosExecutionContextValue}
     * can install a different concrete authority without any operation here
     * changing.
     *
     * <p>PLAT036 Slice 3: not {@code final}, because a genuine execution
     * context's frame-backed authority is only obtainable once lowering-time
     * Bytecode local/frame state exists, which is after object construction.
     * {@link #replaceLexicalBindingAuthorityPreservingBindings} performs a
     * backend-private handoff to a replacement authority, preserving any
     * bindings established before root execution. The old authority remains
     * the only visible authority until migration succeeds and the single
     * authority reference is switched.
     */
    private ProtosLexicalBindingAuthority lexicalBindingAuthority;
    private MutationState mutationState = MutationState.OPEN;

    private ProtosObjectValue() {
        this.parent = null;
        this.lexicalBindingAuthority = new ProtosMapBackedLexicalBindingAuthority();
    }

    public ProtosObjectValue(Object parent) {
        this(parent, new ProtosMapBackedLexicalBindingAuthority());
    }

    /**
     * Installs an explicit lexical-binding authority for this object. Reserved
     * for subclasses that need their own authority attachment (for example
     * {@link ProtosExecutionContextValue}); ordinary objects always use the
     * public single-argument constructor and the default map-backed authority.
     */
    protected ProtosObjectValue(Object parent, ProtosLexicalBindingAuthority lexicalBindingAuthority) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.lexicalBindingAuthority = Objects.requireNonNull(lexicalBindingAuthority, "lexicalBindingAuthority");
    }

    public static ProtosObjectValue rootObject() {
        return ROOT;
    }

    public boolean isRootObject() {
        return this == ROOT;
    }

    public Optional<Object> parent() {
        return Optional.ofNullable(parent);
    }

    public MutationState mutationState() {
        return mutationState;
    }

    public boolean isOpen() {
        return mutationState == MutationState.OPEN;
    }

    public boolean isClosed() {
        return mutationState == MutationState.CLOSED;
    }

    public boolean isFrozen() {
        return mutationState == MutationState.FROZEN;
    }

    public ProtosObjectValue close() {
        if (mutationState == MutationState.OPEN) {
            mutationState = MutationState.CLOSED;
        }
        return this;
    }

    public ProtosObjectValue freeze() {
        mutationState = MutationState.FROZEN;
        return this;
    }

    public boolean hasLocalSlot(String name) {
        Objects.requireNonNull(name, "name");
        return lexicalBindingAuthority.containsBinding(name);
    }

    public Optional<Object> readLocalSlot(String name) {
        Objects.requireNonNull(name, "name");
        return lexicalBindingAuthority.readBinding(name);
    }

    public Map<String, Object> localSlotsSnapshot() {
        return lexicalBindingAuthority.bindingsSnapshot();
    }

    public ProtosObjectValue withoutLocalSlot(String name) {
        Objects.requireNonNull(name, "name");
        if (!lexicalBindingAuthority.containsBinding(name)) {
            throw new IllegalStateException("local slot does not exist: " + name);
        }

        ProtosObjectValue result = new ProtosObjectValue(rootObject());
        for (Map.Entry<String, Object> entry : lexicalBindingAuthority.bindingsSnapshot().entrySet()) {
            if (!entry.getKey().equals(name)) {
                result.createLocalSlot(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public ProtosObjectValue aliasLocalSlot(
            String sourceName,
            String aliasName) {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(aliasName, "aliasName");
        if (!lexicalBindingAuthority.containsBinding(sourceName)) {
            throw new IllegalStateException("local slot does not exist: " + sourceName);
        }
        if (lexicalBindingAuthority.containsBinding(aliasName)) {
            throw new IllegalStateException("local slot already exists: " + aliasName);
        }

        ProtosObjectValue result = new ProtosObjectValue(rootObject());
        Map<String, Object> snapshot = lexicalBindingAuthority.bindingsSnapshot();
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            result.createLocalSlot(entry.getKey(), entry.getValue());
        }
        result.createLocalSlot(aliasName, snapshot.get(sourceName));
        return result;
    }

    public void composeLocalSlotsFrom(
            ProtosObjectValue source,
            java.util.Set<String> reservedNames) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reservedNames, "reservedNames");

        if (mutationState == MutationState.FROZEN) {
            throw new IllegalStateException("object is frozen");
        }
        if (mutationState == MutationState.CLOSED) {
            throw new IllegalStateException("object is closed");
        }

        Map<String, Object> contributions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.lexicalBindingAuthority.bindingsSnapshot().entrySet()) {
            if (!reservedNames.contains(entry.getKey())) {
                contributions.put(entry.getKey(), entry.getValue());
            }
        }

        for (String name : contributions.keySet()) {
            if (lexicalBindingAuthority.containsBinding(name)) {
                throw new IllegalStateException("composition conflict: " + name);
            }
        }

        for (Map.Entry<String, Object> entry : contributions.entrySet()) {
            lexicalBindingAuthority.putBinding(entry.getKey(), entry.getValue());
        }
    }

    public Optional<ProtosSlotLookupResult> lookupSlot(String name) {
        Objects.requireNonNull(name, "name");

        ProtosObjectValue current = this;
        while (true) {
            Optional<Object> local = current.lexicalBindingAuthority.readBinding(name);
            if (local.isPresent()) {
                return Optional.of(new ProtosSlotLookupResult(local.get(), current));
            }

            if (current.parent == null) {
                return Optional.empty();
            }

            if (!(current.parent instanceof ProtosObjectValue parentObject)) {
                throw new UnsupportedOperationException(
                        "Delegated lookup through non-ordinary Protos values "
                                + "requires standard prototype bootstrap");
            }

            current = parentObject;
        }
    }

    public Optional<Object> readSlot(String name) {
        return lookupSlot(name).map(ProtosSlotLookupResult::value);
    }

    public void createLocalSlot(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");

        if (mutationState == MutationState.FROZEN) {
            throw new IllegalStateException("object is frozen");
        }
        if (mutationState == MutationState.CLOSED) {
            throw new IllegalStateException("object is closed");
        }
        if (lexicalBindingAuthority.containsBinding(name)) {
            throw new IllegalStateException("local slot already exists: " + name);
        }

        lexicalBindingAuthority.putBinding(name, value);
    }

    public void assignLocalSlot(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");

        if (mutationState == MutationState.FROZEN) {
            throw new IllegalStateException("object is frozen");
        }
        if (!lexicalBindingAuthority.containsBinding(name)) {
            throw new IllegalStateException("local slot does not exist: " + name);
        }

        lexicalBindingAuthority.putBinding(name, value);
    }

    public Object removeLocalSlot(String name) {
        Objects.requireNonNull(name, "name");

        if (mutationState == MutationState.FROZEN) {
            throw new IllegalStateException("object is frozen");
        }
        if (mutationState == MutationState.CLOSED) {
            throw new IllegalStateException("object is closed");
        }
        if (!lexicalBindingAuthority.containsBinding(name)) {
            throw new IllegalStateException("local slot does not exist: " + name);
        }

        return lexicalBindingAuthority.removeBinding(name);
    }

    /**
     * PLAT036 Candidate D, Slice 3 backend-private one-time authority
     * replacement, reserved for a subclass (currently only {@link
     * ProtosExecutionContextValue}) that discovers its true authoritative
     * storage only after construction (a Truffle Bytecode DSL frame/local
     * layout, known only once the owning generated root begins executing).
     * Rejected once any binding has been established, so no caller can ever
     * observe two different authoritative values for the same binding name.
     */
    protected final void replaceLexicalBindingAuthorityPreservingBindings(
            ProtosLexicalBindingAuthority replacement) {
        Objects.requireNonNull(replacement, "replacement");

        /*
         * The current authority remains the sole visible authority while the
         * replacement is populated. Only after migration succeeds is the
         * authority pointer switched. The old authority then becomes
         * unreachable from this object.
         */
        Map<String, Object> existing =
                lexicalBindingAuthority.bindingsSnapshot();

        for (Map.Entry<String, Object> entry : existing.entrySet()) {
            replacement.putBinding(entry.getKey(), entry.getValue());
        }

        this.lexicalBindingAuthority = replacement;
    }
    /*
     * I026-D1 / PLAT013: tooling observation is local reflection only.
     * Subclasses inherit the export mechanically but receive no object-member
     * projection until their own runtime-family tranche explicitly enables it.
     */
    private boolean supportsInteropObjectMembers() {
        return getClass() == ProtosObjectValue.class;
    }

    @ExportMessage
    boolean hasMembers() {
        return supportsInteropObjectMembers();
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal)
            throws UnsupportedMessageException {
        if (!supportsInteropObjectMembers()) {
            throw UnsupportedMessageException.create();
        }
        return new ProtosInteropMemberNames(localSlotsSnapshot().keySet());
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        if (!supportsInteropObjectMembers()) {
            return false;
        }
        Optional<Object> value = readLocalSlot(member);
        return value.isPresent() && InteropLibrary.isValidValue(value.get());
    }

    @ExportMessage
    Object readMember(String member)
            throws UnknownIdentifierException, UnsupportedMessageException {
        if (!supportsInteropObjectMembers()) {
            throw UnsupportedMessageException.create();
        }
        Optional<Object> value = readLocalSlot(member);
        if (value.isEmpty() || !InteropLibrary.isValidValue(value.get())) {
            throw UnknownIdentifierException.create(member);
        }
        return value.get();
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Object";
    }

}
