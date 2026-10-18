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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosLexicalFallback;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.MaterializedLocalAccessor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/**
 * Captured lexical access: owner-frame selection, nearer-scope absence
 * proofs, captured fallback lookup and captured write targets.
 */
final class ProtosCapturedLexicalAccess {
    private ProtosCapturedLexicalAccess() {}

    /**
     * The statically proven direct-read primitive shared by {@link
     * ReadCapturedFrameLocal} and its PERF025 inline-callback counterpart,
     * once the current scope is known not to hold {@code name}: the owner
     * binding, or {@code null} when any nearer captured scope holds the name
     * PRESENT or the owner/layout does not match (the caller then takes the
     * exact fallback).
     */
    static Object readCapturedFrameBindingOrNull(
            ProtosLexicalEnvironment captured,
            String name,
            int lexicalDepth,
            int frameOrdinal) {
        return readOwnerFrameBindingOrNull(
                capturedOwnerWithoutNearerBinding(captured, name, lexicalDepth - 1),
                name,
                frameOrdinal);
    }

    /**
     * The owner half of {@link #readCapturedFrameBindingOrNull}: the binding
     * of an already selected {@code owner} (or {@code null} for none), or
     * {@code null} when its authority or layout does not match.
     */
    static Object readOwnerFrameBindingOrNull(
            ProtosLexicalEnvironment owner,
            String name,
            int frameOrdinal) {
        if (owner != null
                && owner.lexicalBindingAuthorityForRuntime()
                        instanceof ProtosFrameLexicalBindingAuthority authority
                && authority.hasFrameBackedBindingAt(name, frameOrdinal)) {
            return authority.readFrameBackedBindingAt(name, frameOrdinal);
        }
        return null;
    }

    /**
     * The {@link SelectCapturedMaterializedOwnerFrame} counterpart of {@link
     * #readCapturedFrameBindingOrNull}: the retained frame of the selected
     * owner when {@code accessor}'s local is PRESENT in it, otherwise {@code
     * null}.
     */
    static MaterializedFrame capturedMaterializedOwnerFrameOrNull(
            MaterializedLocalAccessor accessor,
            ProtosLexicalEnvironment captured,
            String name,
            int lexicalDepth,
            BytecodeNode bytecodeNode) {
        return ownerMaterializedFrameOrNull(
                accessor,
                capturedOwnerWithoutNearerBinding(captured, name, lexicalDepth - 1),
                bytecodeNode);
    }

    /**
     * The owner half of {@link #capturedMaterializedOwnerFrameOrNull}, for an
     * already selected {@code owner} (or {@code null} for none).
     */
    static MaterializedFrame ownerMaterializedFrameOrNull(
            MaterializedLocalAccessor accessor,
            ProtosLexicalEnvironment owner,
            BytecodeNode bytecodeNode) {
        if (owner == null
                || !(owner.lexicalBindingAuthorityForRuntime()
                        instanceof ProtosFrameLexicalBindingAuthority authority)) {
            return null;
        }
        MaterializedFrame ownerFrame =
                authority.retainedMaterializedFrameForCapturedAccess();
        if (ownerFrame == null || accessor.isCleared(bytecodeNode, ownerFrame)) {
            return null;
        }
        return ownerFrame;
    }

    /**
     * PERF025: selects the statically proven captured owner {@code ownerIndex}
     * scopes outward, or {@code null} when the captured chain is shorter or a
     * semantically nearer captured scope currently has the name PRESENT (D179
     * C0 late creation/removal retargeting). Membership is read through each
     * scope's single authority, so no deferred guest context is materialized.
     */
    private static ProtosLexicalEnvironment capturedOwnerWithoutNearerBinding(
            ProtosLexicalEnvironment captured,
            String name,
            int ownerIndex) {
        ProtosLexicalEnvironment scope = captured;
        for (int index = 0; scope != null && index < ownerIndex; index++) {
            if (scope.hasLocalSlotForRuntime(name)) {
                return null;
            }
            scope = scope.outer();
        }
        return scope;
    }

    /**
     * PERF037-C: a structural proof, retained by one specialized captured
     * read, that {@link #capturedOwnerWithoutNearerBinding} need not ask the
     * semantically nearer scopes for membership.
     *
     * <p>For every nearer scope position the proof records the frame layout
     * its authority stored when the read specialized. A scope whose current
     * authority stores that same layout cannot hold the read's name PRESENT:
     * the name is outside the layout (static resolution selected an outer
     * owner, and specialization re-checks it), and the layout's {@link
     * ProtosFrameLexicalLayout#noDynamicBindingOrNull} token, invalidated
     * before any dynamic-overflow binding is created through any authority
     * of that layout, rules out late creation (D179 C0). Removal can only
     * make a nearer scope ABSENT, which the proof already assumes.
     *
     * <p>The chain itself is never cached: it is the current invocation's
     * captured chain, each position's authority is re-read and checked
     * against the recorded layout, and the owner is the scope reached after
     * the nearer ones, so every invocation selects its own owner, deferred
     * or materialized (both answer through the same single authority).
     * The owner's PRESENT state and value are always read afterwards from
     * that owner, never from the proof. The first scope that does not match
     * permanently retires the proof for this read and resumes the exact
     * generic walk at that scope.
     */
    static final class CapturedNearerScopeAbsence {
        private static final ProtosFrameLexicalLayout[] NO_LAYOUTS =
                new ProtosFrameLexicalLayout[0];
        private static final Assumption[] NO_ASSUMPTIONS = new Assumption[0];

        @com.oracle.truffle.api.CompilerDirectives.CompilationFinal(dimensions = 1)
        private final ProtosFrameLexicalLayout[] nearerLayouts;

        @com.oracle.truffle.api.CompilerDirectives.CompilationFinal(dimensions = 1)
        private final Assumption[] noDynamicBinding;

        private final int ownerIndex;

        @com.oracle.truffle.api.CompilerDirectives.CompilationFinal
        private boolean retired;

        /*
         * PERF037-D: per-site representation profiles of the owner-frame
         * selection. Each records that its branch has been taken at least
         * once; an unseen branch is compiled as a deoptimization instead of
         * a merged path, so a site never pays for owner representations or
         * no-selection outcomes it has not executed. They profile control
         * flow only; the owner, its authority, frame and PRESENT state are
         * always re-read from the current invocation.
         */
        @com.oracle.truffle.api.CompilerDirectives.CompilationFinal
        private boolean seenMaterializedOwner;

        @com.oracle.truffle.api.CompilerDirectives.CompilationFinal
        private boolean seenDeferredOwner;

        @com.oracle.truffle.api.CompilerDirectives.CompilationFinal
        private boolean seenPublishedDeferredOwner;

        @com.oracle.truffle.api.CompilerDirectives.CompilationFinal
        private boolean seenNoSelection;

        private CapturedNearerScopeAbsence(
                ProtosFrameLexicalLayout[] nearerLayouts,
                Assumption[] noDynamicBinding,
                int ownerIndex,
                boolean retired) {
            this.nearerLayouts = nearerLayouts;
            this.noDynamicBinding = noDynamicBinding;
            this.ownerIndex = ownerIndex;
            this.retired = retired;
        }

        /** A proof that never holds; the read always takes the generic walk. */
        static CapturedNearerScopeAbsence unproven(int lexicalDepth) {
            return new CapturedNearerScopeAbsence(
                    NO_LAYOUTS, NO_ASSUMPTIONS, Math.max(lexicalDepth - 1, 0), true);
        }

        /**
         * PERF037-D: the uncached-interpreter instance; never proven and with
         * every branch already seen, so it is never mutated by execution.
         */
        static CapturedNearerScopeAbsence uncached(int lexicalDepth) {
            CapturedNearerScopeAbsence uncached = unproven(lexicalDepth);
            uncached.seenMaterializedOwner = true;
            uncached.seenDeferredOwner = true;
            uncached.seenPublishedDeferredOwner = true;
            uncached.seenNoSelection = true;
            return uncached;
        }

        /**
         * PERF037-D: exactly {@code ownerMaterializedFrameOrNull(accessor,
         * owner, bytecodeNode)} for an owner already selected by {@link
         * #ownerOrNull}, with every branch profiled at this site.
         */
        MaterializedFrame selectOwnerFrameOrNull(
                MaterializedLocalAccessor accessor,
                ProtosLexicalEnvironment owner,
                BytecodeNode bytecodeNode) {
            if (owner != null
                    && ownerAuthorityOrNull(owner)
                            instanceof ProtosFrameLexicalBindingAuthority authority) {
                MaterializedFrame ownerFrame =
                        authority.retainedMaterializedFrameForCapturedAccess();
                if (ownerFrame != null && !accessor.isCleared(bytecodeNode, ownerFrame)) {
                    return ownerFrame;
                }
            }
            profileNoSelection();
            return null;
        }

        /**
         * PERF037-D: records that this site selected no owner frame. Until
         * then the no-selection outcome is compiled as a deoptimization;
         * it changes no binding, owner or frame.
         */
        void profileNoSelection() {
            if (!seenNoSelection) {
                com.oracle.truffle.api.CompilerDirectives.transferToInterpreterAndInvalidate();
                seenNoSelection = true;
            }
        }

        /**
         * Exactly {@code owner.lexicalBindingAuthorityForRuntime()}, with the
         * owner's materialized/deferred representation profiled.
         */
        com.guillermomolina.protos.runtime.ProtosLexicalBindingAuthority
                ownerAuthorityOrNull(ProtosLexicalEnvironment owner) {
            ProtosObjectValue context = owner.materializedContextOrNullForRuntime();
            if (context != null) {
                if (!seenMaterializedOwner) {
                    com.oracle.truffle.api.CompilerDirectives
                            .transferToInterpreterAndInvalidate();
                    seenMaterializedOwner = true;
                }
            } else {
                if (!seenDeferredOwner) {
                    com.oracle.truffle.api.CompilerDirectives
                            .transferToInterpreterAndInvalidate();
                    seenDeferredOwner = true;
                }
                ProtosActivation deferredOwner = owner.deferredOwnerForRuntime();
                context = deferredOwner.materializedContextOrNullForRuntime();
                if (context == null) {
                    return deferredOwner.currentLexicalBindingAuthorityForRuntime();
                }
                if (!seenPublishedDeferredOwner) {
                    com.oracle.truffle.api.CompilerDirectives
                            .transferToInterpreterAndInvalidate();
                    seenPublishedDeferredOwner = true;
                }
            }
            return context
                            instanceof com.guillermomolina.protos.runtime
                                    .ProtosExecutionContextValue executionContext
                    ? executionContext.lexicalBindingAuthorityForRuntime()
                    : null;
        }

        /**
         * Specialization-time construction from the current captured chain.
         * Each token is obtained before the scope's membership is re-checked,
         * so a creation racing with construction either invalidates the
         * token or is observed by the check.
         */
        @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
        static CapturedNearerScopeAbsence create(
                ProtosLexicalEnvironment captured,
                String name,
                int lexicalDepth) {
            int ownerIndex = Math.max(lexicalDepth - 1, 0);
            if (ownerIndex == 0) {
                return new CapturedNearerScopeAbsence(NO_LAYOUTS, NO_ASSUMPTIONS, 0, false);
            }
            ProtosFrameLexicalLayout[] layouts = new ProtosFrameLexicalLayout[ownerIndex];
            Assumption[] tokens = new Assumption[ownerIndex];
            ProtosLexicalEnvironment scope = captured;
            for (int index = 0; index < ownerIndex; index++) {
                if (scope == null
                        || !(scope.lexicalBindingAuthorityForRuntime()
                                instanceof ProtosFrameLexicalBindingAuthority authority)) {
                    return unproven(lexicalDepth);
                }
                ProtosFrameLexicalLayout layout = authority.storedLayout();
                if (layout.offsetOf(name) != null) {
                    return unproven(lexicalDepth);
                }
                Assumption token = layout.noDynamicBindingOrNull();
                if (token == null || scope.hasLocalSlotForRuntime(name)) {
                    return unproven(lexicalDepth);
                }
                layouts[index] = layout;
                tokens[index] = token;
                scope = scope.outer();
            }
            return new CapturedNearerScopeAbsence(layouts, tokens, ownerIndex, false);
        }

        /**
         * Exactly {@code capturedOwnerWithoutNearerBinding(captured, name,
         * lexicalDepth - 1)}.
         */
        @ExplodeLoop
        ProtosLexicalEnvironment ownerOrNull(
                ProtosLexicalEnvironment captured,
                String name) {
            if (!retired) {
                ProtosLexicalEnvironment scope = captured;
                for (int index = 0; index < nearerLayouts.length; index++) {
                    if (scope == null) {
                        return null;
                    }
                    if (!(scope.lexicalBindingAuthorityForRuntime()
                                    instanceof ProtosFrameLexicalBindingAuthority authority)
                            || !authority.storesLayout(nearerLayouts[index])
                            || !noDynamicBinding[index].isValid()) {
                        com.oracle.truffle.api.CompilerDirectives
                                .transferToInterpreterAndInvalidate();
                        retired = true;
                        break;
                    }
                    scope = scope.outer();
                }
                if (!retired) {
                    return scope;
                }
            }
            return capturedOwnerWithoutNearerBinding(captured, name, ownerIndex);
        }
    }

    /**
     * PERF038-D: a monomorphic cache, retained by one specialized captured
     * materialized read, of {@link #ownerMaterializedFrameOrNull}'s owner
     * authority and retained frame.
     *
     * <p>The owner itself is never assumed: it is selected per invocation
     * (through {@link CapturedNearerScopeAbsence}) and compared by identity
     * with the recorded one. A scope's authority changes only when another
     * authority replaces it, which {@link
     * ProtosFrameLexicalBindingAuthority#retireInstallation} reports by
     * invalidating the recorded installation assumption; the scope's own
     * deferred-to-materialized transition keeps the same authority. While
     * the owner matches and the assumption holds, the recorded authority is
     * therefore the owner's current one, and its retained frame is final.
     * Presence (D179 C0 removal) is still read from that frame on every
     * execution. A different owner or a retired installation permanently
     * retires the cache for this read, which then takes the exact generic
     * selection.
     */
    static final class CapturedOwnerFrameCache {
        private static final Entry RETIRED = new Entry(null, null, null, null);

        /*
         * PERF037-D: presentContinuity is the owner layout's continuity token
         * for the read binding's ordinal, admitted only when the binding was
         * physically PRESENT in ownerFrame while the token was valid; null
         * when not admitted, and every hit then checks presence physically.
         */
        private record Entry(
                ProtosLexicalEnvironment owner,
                MaterializedFrame ownerFrame,
                Assumption installed,
                Assumption presentContinuity) {}

        /* One immutable entry, published in a single write; null until first use. */
        @com.oracle.truffle.api.CompilerDirectives.CompilationFinal
        private Entry entry;

        private CapturedOwnerFrameCache(Entry entry) {
            this.entry = entry;
        }

        static CapturedOwnerFrameCache create() {
            return new CapturedOwnerFrameCache(null);
        }

        /** A cache that never records; the read always takes the generic selection. */
        static CapturedOwnerFrameCache retired() {
            return new CapturedOwnerFrameCache(RETIRED);
        }

        /**
         * Exactly {@code ownerMaterializedFrameOrNull(accessor, owner,
         * bytecodeNode)}. PERF037-D: a miss or a retired cache takes the
         * selection profiled by this read's {@code profile}.
         */
        MaterializedFrame ownerFrameOrNull(
                MaterializedLocalAccessor accessor,
                ProtosLexicalEnvironment owner,
                String name,
                BytecodeNode bytecodeNode,
                CapturedNearerScopeAbsence profile) {
            Entry current = entry;
            if (current != RETIRED) {
                if (current != null
                        && owner == current.owner()
                        && current.installed().isValid()) {
                    MaterializedFrame ownerFrame = current.ownerFrame();
                    Assumption presentContinuity = current.presentContinuity();
                    if (presentContinuity != null && presentContinuity.isValid()) {
                        return ownerFrame;
                    }
                    if (accessor.isCleared(bytecodeNode, ownerFrame)) {
                        profile.profileNoSelection();
                        return null;
                    }
                    return ownerFrame;
                }
                com.oracle.truffle.api.CompilerDirectives.transferToInterpreterAndInvalidate();
                entry = current == null ? record(accessor, owner, name, bytecodeNode) : RETIRED;
            }
            return profile.selectOwnerFrameOrNull(accessor, owner, bytecodeNode);
        }

        @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
        private static Entry record(
                MaterializedLocalAccessor accessor,
                ProtosLexicalEnvironment owner,
                String name,
                BytecodeNode bytecodeNode) {
            if (owner == null
                    || !(owner.lexicalBindingAuthorityForRuntime()
                            instanceof ProtosFrameLexicalBindingAuthority authority)) {
                return RETIRED;
            }
            /*
             * The assumption is obtained before the authority is re-read, so
             * a replacement racing with recording either invalidates it or is
             * observed by the re-read.
             */
            Assumption installed = authority.installedAssumption();
            MaterializedFrame ownerFrame = authority.retainedMaterializedFrameForCapturedAccess();
            if (ownerFrame == null
                    || !installed.isValid()
                    || owner.lexicalBindingAuthorityForRuntime() != authority) {
                return RETIRED;
            }
            return new Entry(
                    owner,
                    ownerFrame,
                    installed,
                    admittedPresentContinuityOrNull(
                            accessor, authority, ownerFrame, name, bytecodeNode));
        }

        /**
         * PERF037-D: the continuity token of {@code name}'s ordinal in the
         * authority's frame-backed layout, admitted only when the binding is
         * physically PRESENT in {@code ownerFrame} and the token, obtained
         * before that check, is still valid after it. A valid token alone
         * does not prove presence: an ordinal never established is ABSENT
         * with its token intact. Removal invalidates the token before
         * clearing the local (PERF025-D179-A) and it is never renewed, so an
         * admitted valid token implies the binding is still PRESENT.
         */
        private static Assumption admittedPresentContinuityOrNull(
                MaterializedLocalAccessor accessor,
                ProtosFrameLexicalBindingAuthority authority,
                MaterializedFrame ownerFrame,
                String name,
                BytecodeNode bytecodeNode) {
            ProtosFrameLexicalLayout layout = authority.storedLayout();
            Integer ordinal = layout.offsetOf(name);
            if (ordinal == null) {
                return null;
            }
            Assumption presentContinuity = layout.presentContinuityAt(ordinal);
            if (!presentContinuity.isValid()
                    || accessor.isCleared(bytecodeNode, ownerFrame)
                    || !presentContinuity.isValid()) {
                return null;
            }
            return presentContinuity;
        }

        /** PERF037-D test access: the admitted continuity token, or {@code null}. */
        Assumption presentContinuityForTesting() {
            Entry current = entry;
            return current == null ? null : current.presentContinuity();
        }

        /** PERF037-D test access: true once the cache recorded an owner entry. */
        boolean isRecordedForTesting() {
            Entry current = entry;
            return current != null && current != RETIRED;
        }
    }

    /**
     * The generic captured lookup, also taken when {@link
     * SelectCapturedMaterializedOwnerFrame} selected no owner frame.
     */
    static Object lookupCapturedFallback(
            ProtosActivation activation,
            String name) {
        return ProtosLexicalFallback.readByName(activation, name)
                .orElseThrow(
                        () ->
                                new ProtosSignalException(
                                        ProtosCoreErrors.newUnqualifiedLookupError(
                                                activation)));
    }

    /**
     * I068 Slice 5 ephemeral write destination. It exists only inside one
     * executing activation; it is never stored in a semantic Closure value.
     *
     * <p>For a proven captured frame binding, {@code frameAuthority} and
     * {@code frameOwner} identify the exact outer destination selected before
     * RHS evaluation. The statically proven frame ordinal is deliberately not
     * retained here: PERF030-J supplies it again as the assignment operation's
     * constant operand, so the {@link LocalRangeAccessor} index never comes
     * from runtime destination state. For a PERF013 Slice B2 proven same-group
     * captured binding, {@code materializedOwnerFrame} identifies the exact
     * owner {@link MaterializedFrame} already selected before RHS evaluation,
     * to be revalidated and written through the assignment site's own {@link
     * MaterializedLocalAccessor} constant operand rather than re-resolved.
     * Otherwise {@code target} preserves the exact generic destination chosen
     * before RHS evaluation.
     */
    public static final class CapturedLexicalWriteTarget {
        private final ProtosObjectValue target;
        private final ProtosLexicalEnvironment frameOwner;
        private final ProtosFrameLexicalBindingAuthority frameAuthority;
        private final MaterializedFrame materializedOwnerFrame;

        private CapturedLexicalWriteTarget(
                ProtosObjectValue target,
                ProtosLexicalEnvironment frameOwner,
                ProtosFrameLexicalBindingAuthority frameAuthority,
                MaterializedFrame materializedOwnerFrame) {
            this.target = target;
            this.frameOwner = frameOwner;
            this.frameAuthority = frameAuthority;
            this.materializedOwnerFrame = materializedOwnerFrame;
        }

        static CapturedLexicalWriteTarget generic(
                ProtosObjectValue target) {
            return new CapturedLexicalWriteTarget(
                    java.util.Objects.requireNonNull(target, "target"),
                    null,
                    null,
                    null);
        }

        static CapturedLexicalWriteTarget frameBacked(
                ProtosLexicalEnvironment owner,
                ProtosFrameLexicalBindingAuthority authority) {
            return new CapturedLexicalWriteTarget(
                    null,
                    java.util.Objects.requireNonNull(owner, "owner"),
                    java.util.Objects.requireNonNull(authority, "authority"),
                    null);
        }

        static CapturedLexicalWriteTarget materialized(
                ProtosLexicalEnvironment owner,
                MaterializedFrame ownerFrame) {
            return new CapturedLexicalWriteTarget(
                    null,
                    java.util.Objects.requireNonNull(owner, "owner"),
                    null,
                    java.util.Objects.requireNonNull(ownerFrame, "ownerFrame"));
        }
    }

    /**
     * The statically proven destination-selection primitive shared by {@link
     * ResolveCapturedWritableLexicalTarget} and its PERF025 inline-callback
     * counterpart, once the current scope is known not to hold {@code name}:
     * the frame-backed owner destination, or {@code null} for the exact
     * generic selection.
     */
    static CapturedLexicalWriteTarget capturedFrameWriteTargetOrNull(
            ProtosLexicalEnvironment captured,
            String name,
            int lexicalDepth,
            int frameOrdinal) {
        ProtosLexicalEnvironment owner =
                capturedOwnerWithoutNearerBinding(captured, name, lexicalDepth - 1);
        if (owner != null
                && owner.lexicalBindingAuthorityForRuntime()
                        instanceof ProtosFrameLexicalBindingAuthority authority
                && authority.hasFrameBackedBindingAt(name, frameOrdinal)) {
            return CapturedLexicalWriteTarget.frameBacked(owner, authority);
        }
        return null;
    }

    /**
     * Writes exactly the destination selected before RHS evaluation, never
     * re-resolving it; an invalid mutation is reported as {@link
     * IllegalStateException} for the caller to turn into the guest Error.
     */
    static void assignCapturedFrameDestination(
            CapturedLexicalWriteTarget destination,
            String name,
            int frameOrdinal,
            Object value) {
        if (destination.frameAuthority != null) {
            /*
             * Match ProtosObjectValue.assignLocalSlot: CLOSED remains
             * writable, FROZEN does not. Presence/layout are checked
             * again at the actual mutation point.
             */
            if (destination.frameOwner.isFrozenForRuntime()) {
                throw new IllegalStateException("object is frozen");
            }
            destination.frameAuthority.assignFrameBackedBindingAt(
                    name,
                    frameOrdinal,
                    value);
        } else {
            // I091: object slots are guest-observable and never retain a carrier.
            destination.target.assignLocalSlot(
                    name, ProtosNumericValueSupport.guestValue(value));
        }
    }

    /**
     * The {@link ResolveCapturedMaterializedWritableLexicalTarget} counterpart
     * of {@link #capturedFrameWriteTargetOrNull}.
     */
    static CapturedLexicalWriteTarget capturedMaterializedWriteTargetOrNull(
            MaterializedLocalAccessor accessor,
            ProtosLexicalEnvironment captured,
            String name,
            int lexicalDepth,
            BytecodeNode bytecodeNode) {
        ProtosLexicalEnvironment owner =
                capturedOwnerWithoutNearerBinding(captured, name, lexicalDepth - 1);
        if (owner != null
                && owner.lexicalBindingAuthorityForRuntime()
                        instanceof ProtosFrameLexicalBindingAuthority authority) {
            MaterializedFrame ownerFrame =
                    authority.retainedMaterializedFrameForCapturedAccess();
            if (ownerFrame != null && !accessor.isCleared(bytecodeNode, ownerFrame)) {
                return CapturedLexicalWriteTarget.materialized(owner, ownerFrame);
            }
        }
        return null;
    }

    /**
     * The {@link AssignCapturedMaterializedLocal} counterpart of {@link
     * #assignCapturedFrameDestination}.
     */
    static void assignCapturedMaterializedDestination(
            MaterializedLocalAccessor accessor,
            CapturedLexicalWriteTarget destination,
            String name,
            Object value,
            BytecodeNode bytecodeNode) {
        if (destination.materializedOwnerFrame != null) {
            /*
             * Match ProtosObjectValue.assignLocalSlot: CLOSED remains
             * writable, FROZEN does not. Presence is checked again at
             * the actual mutation point, against the exact selected
             * owner, never a different one.
             */
            if (destination.frameOwner.isFrozenForRuntime()) {
                throw new IllegalStateException("object is frozen");
            }
            if (accessor.isCleared(bytecodeNode, destination.materializedOwnerFrame)) {
                throw new IllegalStateException(
                        "captured frame-backed binding is absent or layout metadata mismatched: "
                                + name);
            }
            accessor.setObject(bytecodeNode, destination.materializedOwnerFrame, value);
        } else {
            destination.target.assignLocalSlot(
                    name, ProtosNumericValueSupport.guestValue(value));
        }
    }
}
