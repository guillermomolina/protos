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

import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An ordinary Protos object. It is a Truffle {@link DynamicObject} (following
 * SimpleLanguage {@code SLObject} and GraalPy {@code PythonAbstractObject}):
 * once established, its ordinary local slots are the object's own
 * properties, described by its {@link Shape}. The delegation {@code parent}
 * is a separate semantic field, not part of the {@code Shape}.
 *
 * <p>Subclasses with independent lexical storage (execution contexts) inherit
 * the {@code DynamicObject} representation only to share the object
 * hierarchy; their bindings are never also stored in their {@code Shape}.
 */
@ExportLibrary(InteropLibrary.class)
public class ProtosObjectValue extends DynamicObject implements TruffleObject {
    public enum MutationState {
        OPEN,
        CLOSED,
        FROZEN
    }

    /**
     * Shared representation for an ordinary object that has never established
     * a local binding. It is immutable implementation state: the owning object
     * materializes its object authority (backed by itself) before its first
     * binding is created.
     */
    private enum EmptyLexicalBindingAuthority
            implements ProtosLexicalBindingAuthority {
        INSTANCE;

        @Override
        public boolean containsBinding(String name) {
            Objects.requireNonNull(name, "name");
            return false;
        }

        @Override
        public Optional<Object> readBinding(String name) {
            Objects.requireNonNull(name, "name");
            return Optional.empty();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Map<String, Object> bindingsSnapshot() {
            return Map.of();
        }

        @Override
        public void appendBindingNamesTo(
                java.util.ArrayList<String> names) {
            Objects.requireNonNull(names, "names");
        }

        @Override
        public boolean appendBindingNamesToBounded(
                java.util.ArrayList<String> names,
                int remainingBudget) {
            Objects.requireNonNull(names, "names");
            return true;
        }

        @Override
        public void appendBindingsTo(
                java.util.ArrayList<String> names,
                java.util.ArrayList<Object> values) {
            Objects.requireNonNull(names, "names");
            Objects.requireNonNull(values, "values");
        }

        @Override
        public void putBinding(String name, Object value) {
            throw new UnsupportedOperationException(
                    "shared empty lexical authority is immutable");
        }

        @Override
        public Object removeBinding(String name) {
            Objects.requireNonNull(name, "name");
            return null;
        }
    }

    private static final ProtosLexicalBindingAuthority EMPTY_LEXICAL_BINDINGS =
            EmptyLexicalBindingAuthority.INSTANCE;

    /**
     * Root of the shared {@code Shape} tree of every ordinary object. Property
     * assumptions let compiled code depend on the absence of a property.
     * Declared before {@link #ROOT} so the root object can use it.
     */
    private static final Shape INITIAL_SHAPE =
            Shape.newBuilder().propertyAssumptions(true).build();

    private static final ProtosObjectValue ROOT = new ProtosObjectValue();

    // Selector dependency only. The authoritative value remains in ROOT's
    // lexical binding authority. No additional guest binding is stored here.
    private static volatile com.oracle.truffle.api.Assumption rootCallSelectionStable;

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
        super(INITIAL_SHAPE);
        this.parent = null;
        this.lexicalBindingAuthority = EMPTY_LEXICAL_BINDINGS;
    }

    public ProtosObjectValue(Object parent) {
        this(parent, EMPTY_LEXICAL_BINDINGS);
    }

    /**
     * Installs an explicit lexical-binding authority for this object. Reserved
     * for subclasses that need their own authority attachment (for example
     * {@link ProtosExecutionContextValue}); ordinary objects use the shared
     * empty authority until their first local binding and then materialize
     * their object authority, backed by the object itself.
     */
    protected ProtosObjectValue(Object parent, ProtosLexicalBindingAuthority lexicalBindingAuthority) {
        super(INITIAL_SHAPE);
        this.parent = Objects.requireNonNull(parent, "parent");
        this.lexicalBindingAuthority = Objects.requireNonNull(lexicalBindingAuthority, "lexicalBindingAuthority");
    }

    public static ProtosObjectValue rootObject() {
        return ROOT;
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    public static com.oracle.truffle.api.Assumption rootCallSelectionAssumptionForRuntime() {
        synchronized (ROOT) {
            var current = rootCallSelectionStable;
            if (current == null || !current.isValid()) {
                current = com.oracle.truffle.api.Truffle.getRuntime()
                        .createAssumption("canonical Object.call selection");
                rootCallSelectionStable = current;
            }
            return current;
        }
    }

    static void invalidateRootCallSelectionForRuntime() {
        synchronized (ROOT) {
            var previous = rootCallSelectionStable;
            rootCallSelectionStable = null;
            if (previous != null) {
                previous.invalidate();
            }
        }
    }

    public boolean isRootObject() {
        return this == ROOT;
    }

    public Optional<Object> parent() {
        return Optional.ofNullable(parent);
    }

    /**
     * Backend-private direct-parent projection for guarded ordinary lookup.
     *
     * <p>Unlike {@link #parent()}, this avoids Optional construction in the hot
     * shared-inherited member-read guard. The semantic parent remains immutable
     * and the public parent surface is unchanged.
     */
    final Object directParentForGuardedLookup() {
        return parent;
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
        return authorityContains(lexicalBindingAuthority, name);
    }

    /**
     * I114: whether this object owns a local {@code call} slot, answered from
     * the single binding authority without reading the value, traversing
     * parents, or allocating. When the object is its own storage this is the
     * real presence of the {@code call} property in its {@code Shape}; any
     * other authority (for example a frame-backed one) keeps the
     * authoritative {@code contains} query.
     */
    public final boolean hasLocalCallSlotForRuntime() {
        ProtosLexicalBindingAuthority authority = lexicalBindingAuthority;
        if (authority == EMPTY_LEXICAL_BINDINGS) {
            return false;
        }
        if (isObjectAuthority(authority)) {
            return DynamicObject.ContainsKeyNode.getUncached().execute(this, "call");
        }
        return ProtosLexicalBindingAuthorityCalls.contains(authority, "call");
    }

    /**
     * Whether this object's ordinary slots are, or will be once established,
     * its own {@code DynamicObject} properties. Only then does its
     * {@code Shape} describe its slots.
     */
    public final boolean usesObjectStorageForRuntime() {
        ProtosLexicalBindingAuthority authority = lexicalBindingAuthority;
        return authority == EMPTY_LEXICAL_BINDINGS || isObjectAuthority(authority);
    }

    private boolean isObjectAuthority(ProtosLexicalBindingAuthority authority) {
        return authority instanceof ProtosMapBackedLexicalBindingAuthority mapBacked
                && mapBacked.isStoredIn(this);
    }

    public Optional<Object> readLocalSlot(String name) {
        Objects.requireNonNull(name, "name");
        return authorityRead(lexicalBindingAuthority, name);
    }


    public Map<String, Object> localSlotsSnapshot() {
        return lexicalBindingAuthority == EMPTY_LEXICAL_BINDINGS
                ? Map.of()
                : ProtosLexicalBindingAuthorityCalls.snapshot(lexicalBindingAuthority);
    }

    /**
     * Backend-private names-only projection of current local bindings.
     * No guest values are read and no delegation is traversed.
     */
    public final void appendLocalBindingNamesTo(
            java.util.ArrayList<String> names) {
        Objects.requireNonNull(names, "names");
        if (lexicalBindingAuthority != EMPTY_LEXICAL_BINDINGS) {
            ProtosLexicalBindingAuthorityCalls.appendNamesTo(
                    lexicalBindingAuthority, names);
        }
    }

    /**
     * D228-A1 bounded names-only projection of current local bindings for
     * REPL selector completion. No guest values are read, no delegation is
     * traversed and no storage is materialized.
     *
     * @return {@code false} when the PRESENT names exceed
     *     {@code remainingBudget}; {@code names} then holds a partial prefix
     *     that the caller must discard
     */
    public final boolean appendLocalBindingNamesToBounded(
            java.util.ArrayList<String> names,
            int remainingBudget) {
        Objects.requireNonNull(names, "names");
        if (lexicalBindingAuthority == EMPTY_LEXICAL_BINDINGS) {
            return true;
        }
        return ProtosLexicalBindingAuthorityCalls.appendNamesToBounded(
                lexicalBindingAuthority, names, remainingBudget);
    }

    public final void appendLocalBindingsTo(
            java.util.ArrayList<String> names,
            java.util.ArrayList<Object> values) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(values, "values");
        authorityAppendTo(lexicalBindingAuthority, names, values);
    }

    /*
     * TEST009-M: the shared empty authority is answered inline so ordinary
     * never-bound objects stay trivially foldable; every other authority goes
     * through one bounded interface-dispatch choke point.
     */
    private static boolean authorityContains(
            ProtosLexicalBindingAuthority authority, String name) {
        return authority != EMPTY_LEXICAL_BINDINGS
                && ProtosLexicalBindingAuthorityCalls.contains(authority, name);
    }

    private static Optional<Object> authorityRead(
            ProtosLexicalBindingAuthority authority, String name) {
        return authority == EMPTY_LEXICAL_BINDINGS
                ? Optional.empty()
                : ProtosLexicalBindingAuthorityCalls.read(authority, name);
    }

    private static void authorityAppendTo(
            ProtosLexicalBindingAuthority authority,
            java.util.ArrayList<String> names,
            java.util.ArrayList<Object> values) {
        if (authority != EMPTY_LEXICAL_BINDINGS) {
            ProtosLexicalBindingAuthorityCalls.appendTo(authority, names, values);
        }
    }

    /**
     * Backend-private subclass hook for execution machinery that must operate
     * on this object's single lexical-binding authority without introducing a
     * second semantic store.
     */
    protected final ProtosLexicalBindingAuthority lexicalBindingAuthorityForSubclass() {
        return lexicalBindingAuthority;
    }

    /**
     * Returns whether this object currently owns any local binding without
     * allocating a snapshot. Used by runtime representation fast paths only.
     */
    final boolean hasLocalBindingsForRuntime() {
        return !lexicalBindingAuthority.isEmpty();
    }

    /**
     * Returns the current writable authority, materializing the object
     * authority (whose storage is this object itself) exactly when the first
     * ordinary local binding is about to be established. Owners with their
     * own installed authority keep it.
     */
    private ProtosLexicalBindingAuthority writableLexicalBindingAuthority() {
        if (lexicalBindingAuthority == EMPTY_LEXICAL_BINDINGS) {
            lexicalBindingAuthority =
                    new ProtosMapBackedLexicalBindingAuthority(this);
        }
        return lexicalBindingAuthority;
    }

    public ProtosObjectValue withoutLocalSlot(String name) {
        Objects.requireNonNull(name, "name");
        if (!authorityContains(lexicalBindingAuthority, name)) {
            throw new IllegalStateException("local slot does not exist: " + name);
        }

        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        java.util.ArrayList<Object> values = new java.util.ArrayList<>();
        authorityAppendTo(lexicalBindingAuthority, names, values);

        ProtosObjectValue result = new ProtosObjectValue(rootObject());
        for (int index = 0; index < names.size(); index++) {
            String observedName = names.get(index);
            if (!observedName.equals(name)) {
                result.createLocalSlot(observedName, values.get(index));
            }
        }
        return result;
    }

    public ProtosObjectValue aliasLocalSlot(
            String sourceName,
            String aliasName) {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(aliasName, "aliasName");
        if (!authorityContains(lexicalBindingAuthority, sourceName)) {
            throw new IllegalStateException("local slot does not exist: " + sourceName);
        }
        if (authorityContains(lexicalBindingAuthority, aliasName)) {
            throw new IllegalStateException("local slot already exists: " + aliasName);
        }

        Optional<Object> sourceBinding =
                authorityRead(lexicalBindingAuthority, sourceName);
        if (sourceBinding.isEmpty()) {
            throw new IllegalStateException(
                    "local slot does not exist: " + sourceName);
        }
        Object sourceValue = sourceBinding.get();

        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        java.util.ArrayList<Object> values = new java.util.ArrayList<>();
        authorityAppendTo(lexicalBindingAuthority, names, values);

        ProtosObjectValue result = new ProtosObjectValue(rootObject());
        for (int index = 0; index < names.size(); index++) {
            result.createLocalSlot(names.get(index), values.get(index));
        }
        result.createLocalSlot(aliasName, sourceValue);
        return result;
    }

    public void composeLocalSlotsFrom(
            ProtosObjectValue source,
            java.util.Set<String> reservedNames) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reservedNames, "reservedNames");
        composeLocalSlotsFrom(source, java.util.List.copyOf(reservedNames));
    }

    public void composeLocalSlotsFrom(
            ProtosObjectValue source,
            java.util.List<String> reservedNames) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reservedNames, "reservedNames");

        if (mutationState == MutationState.FROZEN) {
            throw new IllegalStateException("object is frozen");
        }
        if (mutationState == MutationState.CLOSED) {
            throw new IllegalStateException("object is closed");
        }

        java.util.ArrayList<String> sourceNames = new java.util.ArrayList<>();
        java.util.ArrayList<Object> sourceValues = new java.util.ArrayList<>();
        authorityAppendTo(source.lexicalBindingAuthority, sourceNames, sourceValues);

        java.util.ArrayList<String> contributionNames = new java.util.ArrayList<>();
        java.util.ArrayList<Object> contributionValues = new java.util.ArrayList<>();

        for (int index = 0; index < sourceNames.size(); index++) {
            String name = sourceNames.get(index);
            if (!containsName(reservedNames, name)) {
                contributionNames.add(name);
                contributionValues.add(sourceValues.get(index));
            }
        }

        for (int index = 0; index < contributionNames.size(); index++) {
            String name = contributionNames.get(index);
            if (authorityContains(lexicalBindingAuthority, name)) {
                throw new IllegalStateException("composition conflict: " + name);
            }
        }

        for (int index = 0; index < contributionNames.size(); index++) {
            ProtosLexicalBindingAuthorityCalls.put(
                    writableLexicalBindingAuthority(),
                    contributionNames.get(index),
                    contributionValues.get(index));
        }
    }

    private static boolean containsName(
            java.util.List<String> names,
            String candidate) {
        for (int index = 0; index < names.size(); index++) {
            if (names.get(index).equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    public Optional<ProtosSlotLookupResult> lookupSlot(String name) {
        Objects.requireNonNull(name, "name");

        ProtosObjectValue current = this;
        while (true) {
            Optional<Object> local = authorityRead(current.lexicalBindingAuthority, name);
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
        if (authorityContains(lexicalBindingAuthority, name)) {
            throw new IllegalStateException("local slot already exists: " + name);
        }

        ProtosLexicalBindingAuthorityCalls.put(
                writableLexicalBindingAuthority(), name, value);
    }

    public void assignLocalSlot(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");

        if (mutationState == MutationState.FROZEN) {
            throw new IllegalStateException("object is frozen");
        }
        if (!authorityContains(lexicalBindingAuthority, name)) {
            throw new IllegalStateException("local slot does not exist: " + name);
        }

        ProtosLexicalBindingAuthorityCalls.put(lexicalBindingAuthority, name, value);
    }

    public Object removeLocalSlot(String name) {
        Objects.requireNonNull(name, "name");

        if (mutationState == MutationState.FROZEN) {
            throw new IllegalStateException("object is frozen");
        }
        if (mutationState == MutationState.CLOSED) {
            throw new IllegalStateException("object is closed");
        }
        if (!authorityContains(lexicalBindingAuthority, name)) {
            throw new IllegalStateException("local slot does not exist: " + name);
        }

        Object removed =
                ProtosLexicalBindingAuthorityCalls.remove(lexicalBindingAuthority, name);
        return removed;
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
        ProtosLexicalBindingAuthority replaced = lexicalBindingAuthority;
        ProtosLexicalBindingAuthorityCalls.transferAll(replaced, replacement);

        this.lexicalBindingAuthority = replacement;
        if (replaced != replacement) {
            replaced.retireInstallation();
        }
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
    Object getMembers(
            @SuppressWarnings("unused") boolean includeInternal,
            @Cached DynamicObject.GetKeyArrayNode getKeyArray)
            throws UnsupportedMessageException {
        if (!supportsInteropObjectMembers()) {
            throw UnsupportedMessageException.create();
        }
        Object[] keys = getKeyArray.execute(this);
        java.util.ArrayList<String> names = new java.util.ArrayList<>(keys.length);
        for (Object key : keys) {
            names.add((String) key);
        }
        return new ProtosInteropMemberNames(names);
    }

    @ExportMessage
    boolean isMemberReadable(
            String member,
            @Cached DynamicObject.ContainsKeyNode containsKey,
            @Cached @Cached.Shared("getMember") DynamicObject.GetNode getMember) {
        if (!supportsInteropObjectMembers() || !containsKey.execute(this, member)) {
            return false;
        }
        return InteropLibrary.isValidValue(getMember.execute(this, member, null));
    }

    @ExportMessage
    Object readMember(
            String member,
            @Cached @Cached.Shared("getMember") DynamicObject.GetNode getMember)
            throws UnknownIdentifierException, UnsupportedMessageException {
        if (!supportsInteropObjectMembers()) {
            throw UnsupportedMessageException.create();
        }
        Object value = getMember.execute(this, member, null);
        if (value == null || !InteropLibrary.isValidValue(value)) {
            throw UnknownIdentifierException.create(member);
        }
        return value;
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Object";
    }

}
