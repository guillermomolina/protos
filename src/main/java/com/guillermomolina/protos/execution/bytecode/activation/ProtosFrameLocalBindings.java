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
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Name-keyed lexical binding support of an executing activation: the
 * multiple-creation prefix observation, the exact write destination selected
 * for a bare assignment, and its assignment.
 *
 * <p>I115-S1c-L: the destination itself is selected by {@link
 * ProtosLexicalSelectionNode}, the single runtime implementation of live
 * lexical name selection. This class only represents and assigns a selected
 * destination; it performs no name traversal.
 */
final class ProtosFrameLocalBindings {
    private ProtosFrameLocalBindings() {}

    /**
     * The exact writable destination of a bare assignment, selected once,
     * before its right-hand side evaluates. Assigning through it never
     * resolves the name again: it fixes the current activation's own scope,
     * one captured lexical scope node, or the receiver object, together with
     * the selected name. The private own-physical-binding marker means the
     * assignment site's own LOCAL binding, which its assignment operation
     * writes directly.
     */
    public static final class ResolvedLexicalWriteTarget {
        private static final ResolvedLexicalWriteTarget OWN_PHYSICAL_BINDING =
                new ResolvedLexicalWriteTarget(false, null, null, null);

        private final boolean currentContext;
        private final ProtosLexicalEnvironment environment;
        private final ProtosObjectValue object;
        private final String name;

        private ResolvedLexicalWriteTarget(
                boolean currentContext,
                ProtosLexicalEnvironment environment,
                ProtosObjectValue object,
                String name) {
            this.currentContext = currentContext;
            this.environment = environment;
            this.object = object;
            this.name = name;
        }

        /** The current activation's own lexical scope (Context or frame view). */
        static ResolvedLexicalWriteTarget currentContext(String name) {
            return new ResolvedLexicalWriteTarget(
                    true, null, null, Objects.requireNonNull(name, "name"));
        }

        /** A captured lexical scope, possibly still deferred; never materialized here. */
        static ResolvedLexicalWriteTarget environment(
                ProtosLexicalEnvironment environment, String name) {
            return new ResolvedLexicalWriteTarget(
                    false,
                    Objects.requireNonNull(environment, "environment"),
                    null,
                    Objects.requireNonNull(name, "name"));
        }

        /** The receiver's own local slot. */
        static ResolvedLexicalWriteTarget object(ProtosObjectValue object, String name) {
            return new ResolvedLexicalWriteTarget(
                    false,
                    null,
                    Objects.requireNonNull(object, "object"),
                    Objects.requireNonNull(name, "name"));
        }

        /** The assignment site's own LOCAL binding, found PRESENT at selection. */
        static ResolvedLexicalWriteTarget ownPhysicalBinding() {
            return OWN_PHYSICAL_BINDING;
        }

        boolean isOwnPhysicalBinding() {
            return this == OWN_PHYSICAL_BINDING;
        }

        /** The selected name; not defined for the own-physical-binding marker. */
        String nameForRuntime() {
            if (name == null) {
                throw new IllegalStateException("the own physical binding carries no name");
            }
            return name;
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

    /** Assigns {@code value} through a resolved generic write target. */
    static Object assignResolvedLexicalTarget(
            ProtosActivation activation,
            ResolvedLexicalWriteTarget destination,
            String name,
            Object value) {
        try {
            assignSelected(activation, destination, name, value);
        } catch (IllegalStateException invalidMutation) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
        return value;
    }

    /*
     * Cold: assignment through a selected destination applies the ordinary
     * object rules of that destination (presence, FROZEN) without
     * materializing a deferred Context.
     */
    @TruffleBoundary
    private static void assignSelected(
            ProtosActivation activation,
            ResolvedLexicalWriteTarget destination,
            String name,
            Object value) {
        if (destination.isOwnPhysicalBinding()) {
            throw new IllegalStateException("the own physical binding is assigned by its site");
        }
        if (destination.currentContext) {
            activation.assignCurrentLocalSlotForRuntime(name, value);
        } else if (destination.environment != null) {
            destination.environment.assignLocalSlotForRuntime(name, value);
        } else {
            destination.object.assignLocalSlot(name, value);
        }
    }
}
