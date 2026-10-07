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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import java.util.Objects;
import java.util.Optional;

/**
 * Ordinary slot lookup across runtime representations whose semantic delegation
 * parent is supplied by the source-backed Core prelude.
 */
public final class ProtosValueLookup {
    private ProtosValueLookup() {}

    /**
     * A D013 selection protected by one dependency shared by every visited
     * ordinary object, including nearer objects where the selector is absent.
     * Checking stability never traverses the delegation chain.
     */
    public record GuardedLookup(
            ProtosSlotLookupResult selected,
            Assumption stability) {}

    /**
     * PERF025-H3A shared inherited selection. The exact direct parent is the
     * shareable semantic anchor; the guarded lookup starts at that parent, so
     * sibling receivers are not registered as dependencies. Each actual
     * receiver separately proves local absence of the selector on every hit.
     */
    public record SharedInheritedLookup(
            Object exactParent,
            GuardedLookup parentLookup) {
        public SharedInheritedLookup {
            Objects.requireNonNull(exactParent, "exactParent");
            Objects.requireNonNull(parentLookup, "parentLookup");
        }

        public ProtosSlotLookupResult selected() {
            return parentLookup.selected();
        }

        public Assumption stability() {
            return parentLookup.stability();
        }
    }

    /**
     * Establishes a cache entry using the same lookup implementation as the
     * generic path. This is specialization-time work, never valid-hit work.
     *
     * <p>Attribute-specific assumption invalidation follows the established
     * Truffle runtime pattern used by GraalPy's MRO attribute caches.
     * Protos parents are immutable; selector mutations are the dependencies
     * for admitted ordinary chains. Represented values and subclass storage
     * remain unsupported here and use generic lookup.
     *
     * @return a protected selection, or null when absent or unsupported
     */
    public static GuardedLookup lookupGuarded(
            Object receiver,
            String name,
            ProtosPrelude prelude) {
        CompilerAsserts.neverPartOfCompilation();
        Assumption stability =
                Truffle.getRuntime().createAssumption("Protos selected slot");
        Optional<ProtosSlotLookupResult> selected =
                lookup(receiver, name, prelude, stability);
        if (selected.isEmpty() || !stability.isValid()) {
            stability.invalidate();
            return null;
        }
        return new GuardedLookup(selected.orElseThrow(), stability);
    }

    /**
     * Establishes a shared inherited-member selection for an exact ordinary
     * receiver. The receiver itself must not own {@code name}; lookup and D013
     * dependency registration begin at its exact direct parent.
     *
     * <p>This deliberately shares only inherited selection. Own-slot reads stay
     * on the exact-receiver PIC because ordinary slot storage is still
     * String-keyed LinkedHashMap storage rather than a shared ordinal layout.
     *
     * @return a shared inherited selection, or null when the shape is not
     *         safely admitted
     */
    public static SharedInheritedLookup lookupGuardedSharedInherited(
            Object receiver,
            String name,
            ProtosPrelude prelude) {
        CompilerAsserts.neverPartOfCompilation();
        Objects.requireNonNull(name, "name");

        if (receiver == null || receiver.getClass() != ProtosObjectValue.class) {
            return null;
        }

        ProtosObjectValue ordinary = (ProtosObjectValue) receiver;
        if (ordinary.hasLocalSlot(name)) {
            return null;
        }

        Object parent = ordinary.directParentForGuardedLookup();
        if (parent == null) {
            return null;
        }

        try {
            GuardedLookup parentLookup =
                    lookupGuarded(parent, name, prelude);
            if (parentLookup == null) {
                return null;
            }
            return new SharedInheritedLookup(parent, parentLookup);
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            return null;
        }
    }

    /**
     * Valid-hit guard for {@link #lookupGuardedSharedInherited}. The exact
     * parent identity is immutable; local selector absence is intentionally
     * checked per receiver so one sibling's shadowing does not invalidate the
     * shared parent-chain selection for every other sibling.
     */
    public static boolean matchesGuardedSharedInherited(
            Object receiver,
            String name,
            SharedInheritedLookup cachedLookup) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(cachedLookup, "cachedLookup");

        if (receiver == null || receiver.getClass() != ProtosObjectValue.class) {
            return false;
        }

        ProtosObjectValue ordinary = (ProtosObjectValue) receiver;
        return ordinary.directParentForGuardedLookup()
                        == cachedLookup.exactParent()
                && !ordinary.hasLocalSlot(name);
    }

    /**
     * Returns whether {@code receiver} is one of the two canonical Boolean
     * identities, the only represented values admitted by
     * {@link #lookupGuardedCanonicalBoolean}.
     */
    public static boolean isCanonicalBoolean(Object receiver) {
        return receiver == ProtosBooleanValue.TRUE || receiver == ProtosBooleanValue.FALSE;
    }

    /**
     * Guarded D013 selection for a canonical {@code true}/{@code false}
     * receiver. Canonical Booleans are represented values with no local slots
     * whose delegation parent is fixed by their representation to the root
     * Object, so the only mutable selection dependencies are the ordinary
     * objects visited from that parent. This runs the same lookup loop as
     * {@link #lookupGuarded}; only the receiver's own represented step is
     * exempt from invalidation. Any other receiver, including any other
     * represented value, is unsupported here and generic lookup remains
     * authoritative.
     *
     * @return a protected selection, or null when absent or unsupported
     */
    public static GuardedLookup lookupGuardedCanonicalBoolean(
            Object receiver,
            String name,
            ProtosPrelude prelude) {
        CompilerAsserts.neverPartOfCompilation();
        if (!isCanonicalBoolean(receiver)
                || delegationParent(receiver, prelude).orElse(null)
                        != ProtosObjectValue.rootObject()) {
            return null;
        }
        Assumption stability =
                Truffle.getRuntime().createAssumption("Protos selected Boolean slot");
        Optional<ProtosSlotLookupResult> selected =
                lookup(receiver, name, prelude, stability, true);
        if (selected.isEmpty() || !stability.isValid()) {
            stability.invalidate();
            return null;
        }
        return new GuardedLookup(selected.orElseThrow(), stability);
    }

    /**
     * Returns whether {@code receiver} belongs to the semantic Integer
     * representation family, the only represented values admitted by
     * {@link #lookupGuardedInteger}. Membership is by representation, not by
     * value or identity: every {@link ProtosIntegerValue} is an immutable
     * carrier with no local slots whose delegation parent is fixed by its
     * representation contract to the owning prelude's Integer prototype.
     */
    public static boolean isInteger(Object receiver) {
        return receiver instanceof ProtosIntegerValue;
    }

    /**
     * Guarded D013 selection for a semantic Integer receiver. The Integer
     * prototype comes from the prelude's frozen bindings, so for one prelude
     * the represented step is the same for every Integer value; the remaining
     * selection dependencies are the ordinary objects visited from that
     * prototype (Integer, Number, root Object). This runs the same lookup loop
     * as {@link #lookupGuarded}; only the receiver's own represented step is
     * exempt from invalidation. The result is valid for any Integer receiver
     * evaluated against the same prelude, not only for {@code receiver}. Any
     * other receiver, including any other represented value, is unsupported
     * here and generic lookup remains authoritative.
     *
     * @return a protected selection, or null when absent or unsupported
     */
    public static GuardedLookup lookupGuardedInteger(
            Object receiver,
            String name,
            ProtosPrelude prelude) {
        CompilerAsserts.neverPartOfCompilation();
        if (!isInteger(receiver)
                || prelude == null
                || delegationParent(receiver, prelude).orElse(null)
                        != prelude.integerPrototype()) {
            return null;
        }
        Assumption stability =
                Truffle.getRuntime().createAssumption("Protos selected Integer slot");
        Optional<ProtosSlotLookupResult> selected =
                lookup(receiver, name, prelude, stability, true);
        if (selected.isEmpty() || !stability.isValid()) {
            stability.invalidate();
            return null;
        }
        return new GuardedLookup(selected.orElseThrow(), stability);
    }

    public static Optional<ProtosSlotLookupResult> lookup(
            Object receiver,
            String name,
            ProtosPrelude prelude) {
        return lookup(receiver, name, prelude, null);
    }

    private static Optional<ProtosSlotLookupResult> lookup(
            Object receiver,
            String name,
            ProtosPrelude prelude,
            Assumption stability) {
        return lookup(receiver, name, prelude, stability, false);
    }

    private static Optional<ProtosSlotLookupResult> lookup(
            Object receiver,
            String name,
            ProtosPrelude prelude,
            Assumption stability,
            // Set only by a family-specific guarded entry point that has already
            // proven the receiver's own represented step is fixed.
            boolean admitRepresentedReceiverStep) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(name, "name");

        Object current = receiver;
        while (true) {
            if (current instanceof ProtosObjectValue ordinary) {
                if (stability != null
                        && !ordinary.trackLookupDependency(name, stability)) {
                    stability.invalidate();
                }
                Optional<Object> local = ordinary.readLocalSlot(name);
                if (local.isPresent()) {
                    // TEST009-AC: local is present here, so orElse(null) yields its non-null
                    // value without a PE-visible NoSuchElementException path.
                    return Optional.of(new ProtosSlotLookupResult(local.orElse(null), ordinary));
                }
            } else if (stability != null
                    && !(admitRepresentedReceiverStep && current == receiver)) {
                stability.invalidate();
            }

            Optional<Object> parent = delegationParent(current, prelude);
            if (parent.isEmpty()) {
                return Optional.empty();
            }
            current = parent.orElseThrow();
        }
    }

    /**
     * Returns the semantic immediate delegation parent used by ordinary lookup.
     *
     * <p>The unique root Object has no parent. Represented semantic values delegate through the
     * parent supplied by their representation contract. The prelude may be {@code null} for a
     * representation whose contract does not require a Core prototype.
     */
    public static Optional<Object> delegationParent(
            Object receiver,
            ProtosPrelude prelude) {
        Objects.requireNonNull(receiver, "receiver");

        if (receiver instanceof ProtosObjectValue ordinary) {
            return ordinary.parent();
        }
        if (receiver instanceof ProtosRepresentedValue represented) {
            return Optional.of(
                    Objects.requireNonNull(
                            representedDelegationParent(represented, prelude),
                            "represented delegation parent"));
        }
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException(
                "Standard delegation parent is not implemented for runtime value representation "
                        + receiver.getClass().getName());
    }

    /*
     * TEST009-M: the represented families do not share one parent rule (root
     * Object, a Prelude prototype, or an instance-retained prototype), so the
     * megamorphic interface call is kept out of partial evaluation.
     */
    @TruffleBoundary
    private static Object representedDelegationParent(
            ProtosRepresentedValue represented,
            ProtosPrelude prelude) {
        return represented.representedDelegationParent(prelude);
    }

    /**
     * Performs one ordinary member read after lookup, including method-Closure
     * binding to the original receiver and the selected slot home.
     */
    public static Optional<Object> readMember(
            Object receiver,
            String name,
            ProtosPrelude prelude) {
        Optional<ProtosSlotLookupResult> result = lookup(receiver, name, prelude);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(materializeMemberRead(receiver, result.orElseThrow()));
    }

    public static Object materializeMemberRead(
            Object receiver,
            ProtosSlotLookupResult result) {
        Object value = result.value();
        if (value instanceof ProtosClosureValue closure) {
            return closure.bindMethod(receiver, result.home());
        }
        return value;
    }
}
