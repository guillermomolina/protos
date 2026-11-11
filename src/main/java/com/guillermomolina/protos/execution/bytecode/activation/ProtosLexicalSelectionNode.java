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
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosModuleBindingLocation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
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
 * object-construction body). Modeled on GraalPy {@code
 * ReadGlobalOrBuiltinNode} and TruffleRuby's cached frame/Binding access.
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
 * <p>Selection is specialized like GraalPy {@code ReadGlobalOrBuiltinNode}:
 * {@link #readCachedOwner} and {@link #selectWriteCachedOwner} cache the
 * captured lexical chain's identity and the exact owning scope node found when
 * they specialized (with its module {@link ProtosModuleBindingLocation} when
 * the owner is the module context). Each execution validates only live
 * membership: the current scope and every nearer scope still hold no storage
 * or no binding of the name (an exploded loop over the cached depth that
 * skips scopes without storage), and the owner still holds it. The selected
 * write destination is cached too, so the fast path allocates nothing.
 * {@link #readCachedReceiver} caches the lexical exhaustion of a chain whose
 * scopes hold no storage at all, leaving only the receiver lookup. The generic
 * specializations implement the same order for genuinely dynamic situations
 * (megamorphic chains, a current scope with storage, a cache miss); only
 * their name traversal runs behind a Truffle boundary.
 */
@GenerateUncached
@GenerateInline(false)
abstract class ProtosLexicalSelectionNode extends Node {

    /**
     * Selects {@code name} from {@code activation}: the value for a read, the
     * {@link ResolvedLexicalWriteTarget} for {@code write}.
     */
    abstract Object execute(ProtosActivation activation, String name, boolean write);

    @Specialization(
            excludeForUncached = true,
            guards = {
                "!write",
                "name == cachedName",
                "activation.capturedLexicalEnvironmentForRuntime() == cachedEnvironment",
                "cachedOwner != null",
                "hasNoCurrentScopeStorage(activation)",
                "nearerScopesLackBinding(cachedEnvironment, cachedDepth, cachedName)"
            },
            limit = "3")
    static Object readCachedOwner(
            ProtosActivation activation,
            String name,
            boolean write,
            @Cached(value = "name", neverDefault = true) String cachedName,
            @Cached(value = "activation.capturedLexicalEnvironmentForRuntime()", neverDefault = false)
                    ProtosLexicalEnvironment cachedEnvironment,
            @Cached(value = "ownerDepth(cachedEnvironment, cachedName)", neverDefault = false)
                    int cachedDepth,
            @Cached(value = "ownerOrNull(cachedEnvironment, cachedDepth)", neverDefault = false)
                    ProtosLexicalEnvironment cachedOwner,
            @Cached(value = "moduleLocationOrNull(cachedOwner, cachedName)", neverDefault = false)
                    ProtosModuleBindingLocation location,
            @Cached DynamicObject.GetNode getNode) {
        Object value = location != null
                ? location.readOrNull(getNode)
                : readOwnerOrNull(cachedOwner, cachedName);
        if (value != null) {
            return value;
        }
        // The cached owner lost the binding: continue the ordinary order.
        return readOrThrow(activation, name);
    }

    @Specialization(
            excludeForUncached = true,
            guards = {
                "!write",
                "name == cachedName",
                "activation.capturedLexicalEnvironmentForRuntime() == cachedEnvironment",
                "cachedDepth < 0",
                "hasNoCurrentScopeStorage(activation)",
                "nearerScopesLackBinding(cachedEnvironment, cachedChainLength, cachedName)"
            },
            limit = "3")
    static Object readCachedReceiver(
            ProtosActivation activation,
            String name,
            boolean write,
            @Cached(value = "name", neverDefault = true) String cachedName,
            @Cached(value = "activation.capturedLexicalEnvironmentForRuntime()", neverDefault = false)
                    ProtosLexicalEnvironment cachedEnvironment,
            @Cached(value = "ownerDepth(cachedEnvironment, cachedName)", neverDefault = false)
                    int cachedDepth,
            @Cached(value = "chainLength(cachedEnvironment)", neverDefault = false)
                    int cachedChainLength) {
        return readReceiverOrThrow(activation, name);
    }

    @Specialization(guards = "!write", replaces = {"readCachedOwner", "readCachedReceiver"})
    static Object read(ProtosActivation activation, String name, boolean write) {
        return readOrThrow(activation, name);
    }

    @Specialization(
            excludeForUncached = true,
            guards = {
                "write",
                "name == cachedName",
                "activation.capturedLexicalEnvironmentForRuntime() == cachedEnvironment",
                "cachedTarget != null",
                "hasNoCurrentScopeStorage(activation)",
                "nearerScopesLackBinding(cachedEnvironment, cachedDepth, cachedName)"
            },
            limit = "3")
    static Object selectWriteCachedOwner(
            ProtosActivation activation,
            String name,
            boolean write,
            @Cached(value = "name", neverDefault = true) String cachedName,
            @Cached(value = "activation.capturedLexicalEnvironmentForRuntime()", neverDefault = false)
                    ProtosLexicalEnvironment cachedEnvironment,
            @Cached(value = "ownerDepth(cachedEnvironment, cachedName)", neverDefault = false)
                    int cachedDepth,
            @Cached(value = "ownerTargetOrNull(cachedEnvironment, cachedDepth, cachedName)", neverDefault = false)
                    ResolvedLexicalWriteTarget cachedTarget,
            @Cached(value = "ownerOrNull(cachedEnvironment, cachedDepth)", neverDefault = false)
                    ProtosLexicalEnvironment cachedOwner) {
        if (ownerHolds(cachedOwner, cachedName)) {
            return cachedTarget;
        }
        return writeTargetOrThrow(activation, name);
    }

    @Specialization(guards = "write", replaces = "selectWriteCachedOwner")
    static Object selectWrite(ProtosActivation activation, String name, boolean write) {
        return writeTargetOrThrow(activation, name);
    }

    // ---- Guards and specialization-time helpers ----------------------------------

    /* The current scope holds nothing while it has neither a Context nor a frame view. */
    static boolean hasNoCurrentScopeStorage(ProtosActivation activation) {
        return activation.materializedContextOrNullForRuntime() == null
                && activation.publishedClosureLexicalAuthorityForRuntime() == null;
    }

    /*
     * Live membership of the nearer scopes: none of the first {@code depth}
     * captured scopes holds the name. A scope without any storage is skipped
     * by field reads alone; only a scope that can hold bindings is queried.
     * The depth is a cached constant, so the loop is exploded.
     */
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

    /* The receiver phase alone, after the cached lexical exhaustion. */
    @TruffleBoundary
    static Object readReceiverOrThrow(ProtosActivation activation, String name) {
        Optional<Object> member = ProtosValueLookup.readMember(
                activation.receiver(), name, activation.preludeOrNullForRuntime());
        if (member.isPresent()) {
            return member.get();
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
