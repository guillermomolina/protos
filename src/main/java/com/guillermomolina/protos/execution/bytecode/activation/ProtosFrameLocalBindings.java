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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.ArrayList;
import java.util.List;

/**
 * Frame-local lexical bindings of an executing activation: Closure parameter
 * binding, current-frame binding creation, persistent frame authority installation, multiple
 * create prefixes and resolved write targets.
 */
final class ProtosFrameLocalBindings {
    private ProtosFrameLocalBindings() {}

    static Object closureRestArray(
            ProtosActivation activation,
            int positionalParametersBeforeRest) {
        List<?> supplied =
                closureArguments(activation);
        int restStart =
                Math.min(
                        positionalParametersBeforeRest,
                        supplied.size());
        ProtosPrelude prelude =
                activation.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "parameter binding requires an owning Core prelude"));
        return prelude.newFrozenArray(
                supplied.subList(
                        restStart,
                        supplied.size()));
    }

    static List<?> closureArguments(
            ProtosActivation activation) {
        return activation.suppliedArgumentsForRuntime();
    }

    static void createClosureParameterSlot(
            ProtosActivation activation,
            String name,
            Object value) {
        try {
            activation.createCurrentLocalSlotForRuntime(name, value);
        } catch (IllegalStateException invalidCreation) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
    }

    /**
     * PERF025-H1: establishes a Closure parameter whose binding identity the
     * lowerer proved to be {@code ordinal} of {@code frameBackedLayout}, the
     * layout of a root that installs its persistent frame authority at entry.
     * While that root's authority is the activation's current one and the
     * context admits creation, the parameter is created at its known ordinal,
     * still rejecting a PRESENT binding exactly as a duplicate creation. Any
     * other state (a non-OPEN context, or another authority) takes the
     * unchanged named path of {@link BindClosureParameter}.
     */
    static void bindIndexedClosureParameter(
            ProtosFrameLexicalLayout frameBackedLayout,
            ProtosActivation activation,
            int ordinal,
            String name,
            Object value) {
        if (activation.currentAuthorityAdmittingLocalCreationForRuntime()
                        instanceof ProtosFrameLexicalBindingAuthority authority
                && authority.storesLayout(frameBackedLayout)) {
            try {
                authority.createFrameBackedBindingAt(ordinal, value);
            } catch (IllegalStateException invalidCreation) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newError(activation));
            }
            return;
        }
        createClosureParameterSlot(activation, name, value);
    }

    /**
     * PERF025 frame-materialization slice: establishes a statically proven
     * current binding of a root lowered without {@link
     * InstallFrameLexicalAuthority} (see {@code
     * CanonicalToBytecodeLowerer#requiresPersistentFrameAuthority}).
     *
     * <p>While the activation's execution context is unobserved and has no
     * authority, the root's frame local is its only possible store: the
     * binding is created there directly, with the exact OPEN-context creation
     * rule (a PRESENT, i.e. non-cleared, local is a duplicate creation; an
     * unobserved context is necessarily OPEN). Allocating the local never made
     * it PRESENT; only this write does.
     *
     * <p>In any other state (a Context already materialized, or an authority
     * already installed), the root first installs, exactly once and while its
     * frame is live, the same materialized-frame authority {@link
     * InstallFrameLexicalAuthority} would have installed at entry, adopting
     * the bindings already established in its locals, and then uses the
     * unchanged authority creation path. No VirtualFrame is ever retained.
     *
     * <p>PERF030-I: every caller passes an {@code ordinal} that is an {@code
     * int} constant operand of its operation, because the {@link
     * LocalRangeAccessor} accesses require a partial-evaluation constant
     * index; it is never read from a runtime operand or an ordinal array.
     */
    static void createCurrentFrameBinding(
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            ProtosActivation activation,
            int ordinal,
            String name,
            Object value,
            BytecodeNode bytecodeNode,
            int bytecodeIndex,
            VirtualFrame frame) {
        if (activation.hasUnobservedFrameNativeExecutionContextForRuntime()) {
            if (!frameBackedLocals.isCleared(bytecodeNode, frame, ordinal)) {
                throw new IllegalStateException("local slot already exists: " + name);
            }
            frameBackedLocals.setObject(bytecodeNode, frame, ordinal, value);
            return;
        }
        if (activation.hasGenuineExecutionContextForRuntime()) {
            installFrameLexicalAuthorityOnTransition(
                    frameBackedLocals,
                    frameBackedLayout,
                    activation,
                    bytecodeNode.getBytecodeLocation(bytecodeIndex),
                    frame.materialize());
        }
        // I091: only unobserved frame-native storage retains a primitive carrier.
        activation.createCurrentLocalSlotForRuntime(
                name, ProtosNumericValueSupport.guestValue(value));
    }

    /**
     * Installs the materialized-frame authority of a live root lowered
     * without {@link InstallFrameLexicalAuthority}, unless the authority
     * already installed is that root's own over this same frame. Callers pass
     * the frame of an activation that is still executing, and a location of
     * that activation's root (BUG018-C retained-frame reads translate it).
     */
    static void installFrameLexicalAuthorityOnTransition(
            LocalRangeAccessor frameBackedLocals,
            ProtosFrameLexicalLayout frameBackedLayout,
            ProtosActivation activation,
            BytecodeLocation location,
            MaterializedFrame materializedFrame) {
        if (activation.currentLexicalBindingAuthorityForRuntime()
                        instanceof ProtosFrameLexicalBindingAuthority installed
                && installed.isInstalledFor(location.getBytecodeNode(), materializedFrame)) {
            return;
        }
        ProtosFrameLexicalBindingAuthority authority =
                new ProtosFrameLexicalBindingAuthority(
                        frameBackedLayout,
                        frameBackedLocals,
                        location,
                        materializedFrame);
        authority.adoptPresentFrameBackedBindings();
        activation.installFrameLexicalBindingAuthorityForRuntime(authority);
    }

    public static final class ResolvedLexicalWriteTarget {
        /**
         * PERF028-A selection of the statically proven current frame local,
         * recognized only by identity in {@link AssignCurrentFrameLocal}. It
         * is stateless because the destination is the assignment site's own
         * constant {@link LocalAccessor} in the executing frame, so the
         * ordinary PRESENT path allocates no destination object.
         */
        static final ResolvedLexicalWriteTarget STATIC_CURRENT_FRAME_LOCAL =
                new ResolvedLexicalWriteTarget(true, null);

        final boolean currentContext;
        final ProtosObjectValue object;

        private ResolvedLexicalWriteTarget(
                boolean currentContext,
                ProtosObjectValue object) {
            this.currentContext = currentContext;
            this.object = object;
        }

        static ResolvedLexicalWriteTarget currentContext() {
            return new ResolvedLexicalWriteTarget(true, null);
        }

        static ResolvedLexicalWriteTarget object(
                ProtosObjectValue object) {
            return new ResolvedLexicalWriteTarget(
                    false,
                    java.util.Objects.requireNonNull(object, "object"));
        }
    }

    static List<Object> observeMultipleCreatePrefix(
            ProtosActivation activation,
            int required,
            Object source) {
        List<Object> observed = multipleCreatePrefixOrNull(required, source);
        if (observed == null) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
        return observed;
    }

    /**
     * The successful D143 prefix observation of {@link
     * #observeMultipleCreatePrefix}, or {@code null} when the source is not an
     * Array holding at least {@code required} elements (an Error case).
     */
    static List<Object> multipleCreatePrefixOrNull(int required, Object source) {
        if (!(source instanceof ProtosArrayValue array)
                || array.indexedSize() < required) {
            return null;
        }

        /*
         * D143 requires the complete fixed prefix to be shallow-observed
         * before the first target slot is created. Do not use at,
         * iteration, indexedSnapshot(), or any guest-visible protocol.
         */
        List<Object> observed = new ArrayList<>(required);
        for (int index = 0; index < required; index++) {
            observed.add(array.indexedAt(index));
        }
        return observed;
    }
}
