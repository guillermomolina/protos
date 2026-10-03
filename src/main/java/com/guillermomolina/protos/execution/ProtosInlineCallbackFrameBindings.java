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
 * the specific language governing rights and limitations under the LICENSE.
 */

package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedInlineLiteralCall;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.ResolvedLexicalWriteTarget;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosLexicalBindingAuthority;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.List;
import java.util.Map;

/**
 * PERF025 lazy inline callback activation: binding operations of a PLAT044
 * B′ inline callback whose statically proven current bindings live in
 * block locals of the enclosing physical root.
 *
 * <p>Every operation receives the invocation's {@link
 * PreparedInlineLiteralCall}, the carrier of its one fresh semantic
 * activation, together with the callback's block-local range and layout.
 * While no activation has been materialized, the block locals are the only
 * possible store of the callback's bindings, exactly as for an unobserved root
 * execution context ({@code ProtosBytecodeRootNode#createCurrentFrameBinding}):
 * no Context exists that could be FROZEN or could have removed a binding, so
 * the operations act on the locals directly.
 *
 * <p>The block locals live only as long as one invocation of the region, and
 * the next invocation reuses them. The activation is therefore only ever
 * materialized through {@link #durableActivation}, which, before returning it
 * to any consumer, moves the PRESENT bindings into a fresh durable map-backed
 * authority installed on it. No consumer can observe the activation, or its
 * Context, while the bindings are still ephemeral; from then on the
 * activation is the sole authority of the invocation and these operations take
 * the unchanged activation semantics. No authority over the block locals or
 * the physical frame is ever installed.
 */
final class ProtosInlineCallbackFrameBindings {
    private ProtosInlineCallbackFrameBindings() {}

    /** Inline counterpart of {@code LoadFrameClosureArgument}. */
    static Object loadArgument(
            PreparedInlineLiteralCall child,
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            int positionalIndex,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        if (!child.isActivationMaterialized()
                && positionalIndex < child.suppliedArgumentCount()) {
            return child.suppliedArgument(positionalIndex);
        }
        return ProtosBytecodeRootNode.LoadClosureArgument.perform(
                durableActivation(
                        child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                positionalIndex);
    }

    /** Inline counterpart of {@code CheckFrameClosureArgumentUpperBound}. */
    static void checkArgumentUpperBound(
            PreparedInlineLiteralCall child,
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            int maximumPositionalArguments,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        if (!child.isActivationMaterialized()
                && child.suppliedArgumentCount() <= maximumPositionalArguments) {
            return;
        }
        ProtosBytecodeRootNode.CheckClosureArgumentUpperBound.perform(
                durableActivation(
                        child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                maximumPositionalArguments);
    }

    /**
     * Establishes a current binding (parameter or target-less creation) with
     * the exact OPEN-context creation rule: a PRESENT binding is a duplicate
     * creation Error.
     */
    static void create(
            PreparedInlineLiteralCall child,
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            int ordinal,
            String name,
            Object value,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        if (!child.isActivationMaterialized()
                && frameBackedLocals.isCleared(bytecodeNode, frame, ordinal)) {
            frameBackedLocals.setObject(bytecodeNode, frame, ordinal, value);
            return;
        }
        ProtosActivation activation =
                durableActivation(
                        child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame);
        try {
            activation.createCurrentLocalSlotForRuntime(name, value);
        } catch (IllegalStateException invalidCreation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
    }

    /**
     * Multiple creation: the complete fixed prefix is observed before the
     * first binding is created, then each binding is created in order with no
     * rollback, as {@code MultipleCreateFrameLocals}.
     */
    static Object multipleCreate(
            PreparedInlineLiteralCall child,
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            int[] ordinals,
            String[] names,
            Object source,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        List<Object> observed =
                ProtosBytecodeRootNode.observeMultipleCreatePrefix(
                        durableActivation(
                                child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                        names.length,
                        source);
        for (int index = 0; index < names.length; index++) {
            create(
                    child,
                    frameBackedLocals,
                    frameBackedLayout,
                    ordinals[index],
                    names[index],
                    observed.get(index),
                    bytecodeNode,
                    frame);
        }
        return source;
    }

    /**
     * Read of a binding statically {@code Resolved} in the callback's own
     * scope, with the D179 C0 presence rule of {@code ReadFrameLocal}: a
     * PRESENT binding is read directly, and once continuity has been
     * invalidated a cleared (ABSENT) one takes the exact lexical/receiver
     * fallback.
     */
    static Object read(
            PreparedInlineLiteralCall child,
            LocalAccessor accessor,
            Assumption presenceContinuity,
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            String name,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        if (!child.isActivationMaterialized()
                && (presenceContinuity.isValid() || !accessor.isCleared(bytecodeNode, frame))) {
            return accessor.getObject(bytecodeNode, frame);
        }
        return ProtosBytecodeRootNode.Lookup.perform(
                durableActivation(
                        child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                name);
    }

    /**
     * PERF028-A destination selection, before RHS evaluation: a PRESENT
     * frame-native binding selects the static current local; any other state
     * runs the exact generic selection now, and that selection is retained.
     */
    static ResolvedLexicalWriteTarget resolveWriteTarget(
            PreparedInlineLiteralCall child,
            LocalAccessor accessor,
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            String name,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        if (!child.isActivationMaterialized() && !accessor.isCleared(bytecodeNode, frame)) {
            return ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL;
        }
        return ProtosBytecodeRootNode.ResolveWritableLexicalTarget.perform(
                durableActivation(
                        child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame),
                name);
    }

    /**
     * Writes exactly the destination selected by {@link #resolveWriteTarget},
     * never re-resolving it. The static current local denotes this callback's
     * own binding: a binding cleared by the RHS is a mutation Error, not a
     * retarget. If the RHS materialized the activation, that same binding is
     * now in the durable authority and is written there under the FROZEN and
     * presence rules of {@code AssignCurrentFrameLocal}.
     */
    static Object assign(
            PreparedInlineLiteralCall child,
            LocalAccessor accessor,
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            ResolvedLexicalWriteTarget destination,
            String name,
            Object value,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        if (destination == ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL
                && !child.isActivationMaterialized()
                && !accessor.isCleared(bytecodeNode, frame)) {
            accessor.setObject(bytecodeNode, frame, value);
            return value;
        }

        ProtosActivation activation =
                durableActivation(
                        child, frameBackedLocals, frameBackedLayout, bytecodeNode, frame);
        if (destination != ResolvedLexicalWriteTarget.STATIC_CURRENT_FRAME_LOCAL) {
            return ProtosBytecodeRootNode.AssignResolvedLexicalTarget.perform(
                    activation, destination, name, value);
        }
        if (activation.currentContextIsFrozenForRuntime()
                || !activation.currentContextHasLocalSlotForRuntime(name)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        try {
            activation.assignCurrentLocalSlotForRuntime(name, value);
        } catch (IllegalStateException invalidMutation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return value;
    }

    /**
     * The only guest path that materializes the activation of a frame-native
     * inline callback. Returns the invocation's one activation (materialized
     * and published by the carrier on first use) only after this invocation's
     * PRESENT block-local bindings have been moved, in layout (declaration)
     * order, into a durable map-backed authority installed on it, and the
     * block locals have been cleared. Runs the transfer at most once per
     * invocation; every later call returns the same activation.
     *
     * <p>Layout order is establishment order: within one invocation the
     * callback establishes its bindings in straight-line code, and nothing can
     * remove one before its Context exists.
     */
    static ProtosActivation durableActivation(
            PreparedInlineLiteralCall child,
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        if (child.frameBindingsTransferred()) {
            return child.activation();
        }
        ProtosLexicalBindingAuthority durable =
                ProtosLexicalBindingAuthority.newMapBackedForRuntime();
        for (int ordinal = 0; ordinal < frameBackedLayout.length(); ordinal++) {
            if (frameBackedLocals.isCleared(bytecodeNode, frame, ordinal)) {
                continue;
            }
            durable.putBinding(
                    frameBackedLayout.nameAt(ordinal),
                    frameBackedLocals.getObject(bytecodeNode, frame, ordinal));
            frameBackedLocals.clear(bytecodeNode, frame, ordinal);
        }
        return publishDurable(child, durable);
    }

    /**
     * Tooling counterpart of {@link #durableActivation}: a scope query may be
     * the invocation's first observer. {@code presentBindings} is the
     * suspension-time projection of the PRESENT block locals, in layout order.
     * The locals are not cleared through the tooling frame API; they are
     * relinquished instead, because every guest operation takes the activation
     * path once the activation exists.
     */
    static ProtosActivation durableActivationForTooling(
            PreparedInlineLiteralCall child,
            Map<String, Object> presentBindings) {
        if (child.frameBindingsTransferred()) {
            return child.activation();
        }
        ProtosLexicalBindingAuthority durable =
                ProtosLexicalBindingAuthority.newMapBackedForRuntime();
        presentBindings.forEach(durable::putBinding);
        return publishDurable(child, durable);
    }

    private static ProtosActivation publishDurable(
            PreparedInlineLiteralCall child,
            ProtosLexicalBindingAuthority durable) {
        ProtosActivation activation = child.activation();
        child.markFrameBindingsTransferred();
        activation.installFrameLexicalBindingAuthorityForRuntime(durable);
        return activation;
    }
}
