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

package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.execution.ProtosFrameLocalBindings.ResolvedLexicalWriteTarget;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.LexicalFrameLayout;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosModuleBindingLocation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import java.util.Optional;

/**
 * I115-S1c-L: the single runtime implementation of live lexical name
 * selection, for every bare read or assignment whose actual nearest binding
 * is not already proven by its physical binding identity (a {@code Dynamic}
 * name, a {@code Candidate} or removable binding found ABSENT, a site behind
 * a Context that may hold a nearer dynamic binding, an inline
 * object-construction body).
 *
 * <p>The semantic traversal (EXECUTION_AND_CONTROL.md §§6-7) is:
 * <ol>
 *   <li>the current genuine execution context's own bindings (its Context,
 *       or the frame-backed view of its frame while the Context is
 *       deferred);</li>
 *   <li>the captured lexical scopes, nearest to outermost, each with only its
 *       own bindings, read through the scope's live owner and never
 *       materializing a deferred Context;</li>
 *   <li>for a read only: ordinary member lookup and delegation of the current
 *       receiver, with the Prelude of the activation (exactly as every other
 *       receiver lookup);</li>
 *   <li>otherwise the standard {@code SlotNotFound}.</li>
 * </ol>
 * A PRESENT guest {@code null} is selected like any other value.
 *
 * <p>Read selection returns the selected value. Write-target selection
 * returns the exact writable destination ({@link ResolvedLexicalWriteTarget})
 * and never consults receiver delegation, only the receiver's own local slot;
 * the caller selects it before the right-hand side evaluates and assigns
 * through it without selecting again.
 *
 * <p>Selection is specialized:
 * {@link #readCachedOwner} and {@link #selectWriteCachedOwner} cache the
 * captured lexical chain's identity and the exact owning scope node found when
 * they specialized (with its module {@link ProtosModuleBindingLocation} when
 * the owner is the module context). Each execution validates only live
 * membership: the current scope and every nearer scope still hold no storage
 * or no binding of the name (an exploded loop over the cached depth that
 * skips scopes without storage), and the owner still holds it. The selected
 * write destination is cached too, so the fast path allocates nothing.
 * {@link #readCachedReceiver} caches lexical exhaustion while validating live
 * absence in every captured scope, leaving only the receiver lookup. The generic
 * specializations implement the same order for genuinely dynamic situations
 * (megamorphic chains, a current scope with storage, a cache miss); only
 * their name traversal runs behind a Truffle boundary.
 */
@GenerateUncached
@GenerateInline(false)
abstract class ProtosLexicalSelectionNode extends Node {

    /**
     * Selects from an exact activation or the running root's admitted B5 arguments.
     * A Java null result requests the existing full activation-based resolution;
     * guest null is a non-null value. Without the executing root's physical
     * layout, a compact state always requests full resolution. Returns the value
     * for a read, the {@link ResolvedLexicalWriteTarget} for {@code write}.
     */
    final Object execute(Object activation, String name, boolean write) {
        return execute(activation, name, write, null);
    }

    /** The layout is the executing root's existing layout, never another owner's. */
    abstract Object execute(
            Object activation, String name, boolean write, LexicalFrameLayout currentLayout);

    @Specialization(
            guards = {
                "!write",
                "name == cachedName",
                "capturedEnvironment(activation) == cachedEnvironment",
                "cachedOwner != null",
                "hasNoCurrentScopeStorage(activation, name, currentLayout)",
                "nearerScopesLackBinding(cachedEnvironment, cachedDepth, cachedName)"
            },
            limit = "3")
    static Object readCachedOwner(
            Object activation,
            String name,
            boolean write,
            LexicalFrameLayout currentLayout,
            @Cached(value = "name", uncached = "name") String cachedName,
            @Cached(value = "capturedEnvironment(activation)", uncached = "capturedEnvironment(activation)")
                    ProtosLexicalEnvironment cachedEnvironment,
            @Cached(value = "ownerDepth(cachedEnvironment, cachedName)", uncached = "ownerDepth(cachedEnvironment, cachedName)")
                    int cachedDepth,
            @Cached(value = "ownerOrNull(cachedEnvironment, cachedDepth)", uncached = "ownerOrNull(cachedEnvironment, cachedDepth)")
                    ProtosLexicalEnvironment cachedOwner,
            @Cached(value = "moduleLocationOrNull(cachedOwner, cachedName)", uncached = "moduleLocationOrNull(cachedOwner, cachedName)")
                    ProtosModuleBindingLocation location,
            @Cached DynamicObject.GetNode getNode) {
        Object value = location != null && location.hasCurrentStorage()
                ? location.readOrNull(getNode)
                : readOwnerOrNull(cachedOwner, cachedName);
        if (value != null) {
            return value;
        }
        // The cached owner lost the binding: continue the ordinary order.
        return read(activation, name, false, currentLayout);
    }

    @Specialization(
            guards = {
                "!write",
                "name == cachedName",
                "capturedEnvironment(activation) == cachedEnvironment",
                "cachedDepth < 0",
                "hasReceiverPrelude(activation)",
                "hasNoCurrentScopeStorage(activation, name, currentLayout)",
                "nearerScopesLackBinding(cachedEnvironment, cachedChainLength, cachedName)"
            },
            limit = "3")
    static Object readCachedReceiver(
            Object activation,
            String name,
            boolean write,
            LexicalFrameLayout currentLayout,
            @Cached(value = "name", uncached = "name") String cachedName,
            @Cached(value = "capturedEnvironment(activation)", uncached = "capturedEnvironment(activation)")
                    ProtosLexicalEnvironment cachedEnvironment,
            @Cached(value = "ownerDepth(cachedEnvironment, cachedName)", uncached = "ownerDepth(cachedEnvironment, cachedName)")
                    int cachedDepth,
            @Cached(value = "chainLength(cachedEnvironment)", uncached = "chainLength(cachedEnvironment)")
                    int cachedChainLength) {
        return readReceiverOrThrow(activation, name);
    }

    @Specialization(guards = "!write", replaces = {"readCachedOwner", "readCachedReceiver"})
    static Object read(
            Object activation, String name, boolean write, LexicalFrameLayout currentLayout) {
        ProtosActivation published = publishedActivationOrNull(activation);
        return published == null ? null : readOrThrow(published, name);
    }

    @Specialization(
            guards = {
                "write",
                "name == cachedName",
                "capturedEnvironment(activation) == cachedEnvironment",
                "cachedTarget != null",
                "hasNoCurrentScopeStorage(activation, name, currentLayout)",
                "nearerScopesLackBinding(cachedEnvironment, cachedDepth, cachedName)"
            },
            limit = "3")
    static Object selectWriteCachedOwner(
            Object activation,
            String name,
            boolean write,
            LexicalFrameLayout currentLayout,
            @Cached(value = "name", uncached = "name") String cachedName,
            @Cached(value = "capturedEnvironment(activation)", uncached = "capturedEnvironment(activation)")
                    ProtosLexicalEnvironment cachedEnvironment,
            @Cached(value = "ownerDepth(cachedEnvironment, cachedName)", uncached = "ownerDepth(cachedEnvironment, cachedName)")
                    int cachedDepth,
            @Cached(value = "ownerTargetOrNull(cachedEnvironment, cachedDepth, cachedName)", uncached = "ownerTargetOrNull(cachedEnvironment, cachedDepth, cachedName)")
                    ResolvedLexicalWriteTarget cachedTarget,
            @Cached(value = "ownerOrNull(cachedEnvironment, cachedDepth)", uncached = "ownerOrNull(cachedEnvironment, cachedDepth)")
                    ProtosLexicalEnvironment cachedOwner) {
        if (ownerHolds(cachedOwner, cachedName)) {
            return cachedTarget;
        }
        return selectWrite(activation, name, true, currentLayout);
    }

    @Specialization(guards = "write", replaces = "selectWriteCachedOwner")
    static Object selectWrite(
            Object activation, String name, boolean write, LexicalFrameLayout currentLayout) {
        ProtosActivation published = publishedActivationOrNull(activation);
        return published == null ? null : writeTargetOrThrow(published, name);
    }

    // ---- Guards and specialization-time helpers ----------------------------------

    /*
     * An unpublished invocation can still own LOCAL/CELL bindings. Only its
     * existing physical layout can exclude the searched name. Publication is
     * re-read on each execution, because Context or frame authority may appear.
     */
    @NonIdempotent
    static boolean hasNoCurrentScopeStorage(
            Object state, String name, LexicalFrameLayout currentLayout) {
        ProtosActivation activation = publishedActivationOrNull(state);
        if (activation != null) {
            return activation.materializedContextOrNullForRuntime() == null
                    && activation.publishedClosureLexicalAuthorityForRuntime() == null;
        }
        return currentLayout != null && currentLayout.indexOf(name) < 0;
    }

    @NonIdempotent
    static ProtosActivation publishedActivationOrNull(Object state) {
        if (state instanceof ProtosActivation activation) {
            return activation;
        }
        Object[] arguments = (Object[]) state;
        return ProtosFrameArguments.hasPublishedActivation(arguments)
                ? ProtosFrameArguments.publishedActivation(arguments)
                : null;
    }

    static ProtosLexicalEnvironment capturedEnvironment(Object state) {
        return state instanceof ProtosActivation activation
                ? activation.capturedLexicalEnvironmentForRuntime()
                : ProtosFrameArguments.lexicalSelectionEnvironment((Object[]) state);
    }

    @NonIdempotent
    static boolean hasReceiverPrelude(Object state) {
        return publishedActivationOrNull(state) != null
                || ProtosFrameArguments.admittedCompactCreatorOrNull((Object[]) state)
                        .preludeOrNullForRuntime() != null;
    }

    /*
     * Live membership of the nearer scopes: none of the first {@code depth}
     * captured scopes holds the name. A scope without any storage is skipped
     * by field reads alone; only a scope that can hold bindings is queried.
     * The depth is a cached constant, so the loop is exploded. A nearer
     * binding may be created or removed at any time, so the guard is
     * re-evaluated on every execution.
     */
    @NonIdempotent
    @ExplodeLoop
    static boolean nearerScopesLackBinding(
            ProtosLexicalEnvironment environment, int depth, String name) {
        ProtosLexicalEnvironment node = environment;
        for (int step = 0; step < depth; step++) {
            if (node == null) {
                return true;
            }
            if (node.mayHoldBindingsForRuntime() && ownerHolds(node, name)) {
                return false;
            }
            node = node.outer();
        }
        return true;
    }

    @TruffleBoundary
    static boolean ownerHolds(ProtosLexicalEnvironment owner, String name) {
        return owner.hasLocalSlotForRuntime(name);
    }

    @TruffleBoundary
    static Object readOwnerOrNull(ProtosLexicalEnvironment owner, String name) {
        return owner.readLocalSlotForRuntime(name).orElse(null);
    }

    /* Specialization time only: the depth of the nearest scope holding name, or -1. */
    @TruffleBoundary
    static int ownerDepth(ProtosLexicalEnvironment environment, String name) {
        int depth = 0;
        for (ProtosLexicalEnvironment node = environment; node != null; node = node.outer(), depth++) {
            if (node.hasLocalSlotForRuntime(name)) {
                return depth;
            }
        }
        return -1;
    }

    /* Specialization time only: the number of captured scopes. */
    @TruffleBoundary
    static int chainLength(ProtosLexicalEnvironment environment) {
        int length = 0;
        for (ProtosLexicalEnvironment node = environment; node != null; node = node.outer()) {
            length++;
        }
        return length;
    }

    static ProtosLexicalEnvironment ownerOrNull(ProtosLexicalEnvironment environment, int depth) {
        return depth < 0 ? null : ProtosLexicalEnvironment.at(environment, depth);
    }

    /*
     * Specialization time only: the location of name in a materialized module
     * context owner. A module context never changes authority, so the location
     * stays the binding's address; a Closure owner is read through its scope.
     */
    @TruffleBoundary
    static ProtosModuleBindingLocation moduleLocationOrNull(
            ProtosLexicalEnvironment owner, String name) {
        if (owner == null || owner.outer() != null) {
            return null;
        }
        ProtosObjectValue context = owner.materializedContextOrNullForRuntime();
        return context == null ? null : ProtosModuleBindingLocation.ofOrNull(context, name);
    }

    /* Specialization time only: the cached write destination of the owner. */
    static ResolvedLexicalWriteTarget ownerTargetOrNull(
            ProtosLexicalEnvironment environment, int depth, String name) {
        ProtosLexicalEnvironment owner = ownerOrNull(environment, depth);
        return owner == null ? null : ResolvedLexicalWriteTarget.environment(owner, name);
    }

    // ---- The semantic traversal --------------------------------------------------

    /**
     * Read selection: the currently selected value, including a PRESENT guest
     * {@code null}, or the standard unqualified lookup Error.
     */
    @TruffleBoundary
    static Object readOrThrow(ProtosActivation activation, String name) {
        Optional<Object> selected = readOrEmpty(activation, name);
        if (selected.isPresent()) {
            return selected.get();
        }
        throw new ProtosSignalException(ProtosCoreErrors.newUnqualifiedLookupError(activation));
    }

    /** The selected value of {@code name}, or empty when no binding exists. */
    @TruffleBoundary
    static Optional<Object> readOrEmpty(ProtosActivation activation, String name) {
        Optional<Object> current = activation.readCurrentLocalSlotForRuntime(name);
        if (current.isPresent()) {
            return current;
        }
        for (ProtosLexicalEnvironment lexical = activation.capturedLexicalEnvironmentForRuntime();
                lexical != null;
                lexical = lexical.outer()) {
            Optional<Object> captured = lexical.readLocalSlotForRuntime(name);
            if (captured.isPresent()) {
                return captured;
            }
        }
        return ProtosValueLookup.readMember(
                activation.receiver(), name, activation.preludeOrNullForRuntime());
    }

    /*
     * The receiver phase alone, after proved lexical exhaustion. A compact
     * miss requests exact-activation Error construction by the operation.
     */
    @TruffleBoundary
    static Object readReceiverOrThrow(Object state, String name) {
        ProtosActivation activation = publishedActivationOrNull(state);
        Object receiver;
        ProtosPrelude prelude;
        if (activation != null) {
            receiver = activation.receiver();
            prelude = activation.preludeOrNullForRuntime();
        } else {
            Object[] arguments = (Object[]) state;
            receiver = ProtosFrameArguments.currentReceiver(arguments);
            // The specialization proved an own Prelude: no inherited-caller demand.
            prelude = ProtosFrameArguments.admittedCompactCreatorOrNull(arguments)
                    .preludeOrNullForRuntime();
        }
        Optional<Object> member = ProtosValueLookup.readMember(receiver, name, prelude);
        if (member.isPresent()) {
            return member.get();
        }
        if (activation == null) {
            // Error construction needs the exact activation, supplied by the operation.
            return null;
        }
        throw new ProtosSignalException(ProtosCoreErrors.newUnqualifiedLookupError(activation));
    }

    /**
     * Write-target selection: the exact writable destination of {@code name},
     * or the standard {@code SlotNotFound} Error. Never follows receiver
     * delegation and never materializes a deferred Context.
     */
    @TruffleBoundary
    static ResolvedLexicalWriteTarget writeTargetOrThrow(ProtosActivation activation, String name) {
        if (activation.currentContextHasLocalSlotForRuntime(name)) {
            return ResolvedLexicalWriteTarget.currentContext(name);
        }
        for (ProtosLexicalEnvironment lexical = activation.capturedLexicalEnvironmentForRuntime();
                lexical != null;
                lexical = lexical.outer()) {
            if (lexical.hasLocalSlotForRuntime(name)) {
                return ResolvedLexicalWriteTarget.environment(lexical, name);
            }
        }
        if (activation.receiver() instanceof ProtosObjectValue receiver
                && receiver.hasLocalSlot(name)) {
            return ResolvedLexicalWriteTarget.object(receiver, name);
        }
        throw new ProtosSignalException(ProtosCoreErrors.newSlotNotFound(activation));
    }
}
