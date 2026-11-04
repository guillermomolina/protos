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
import com.guillermomolina.protos.runtime.ProtosLexicalFallback;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayList;
import java.util.List;

/**
 * Name-keyed lexical binding support of an executing activation: the
 * multiple-creation prefix observation and the write-target resolution and
 * assignment of a NAME binding (module context, object body, or dynamic
 * name).
 *
 * <p>I115: LOCAL and CELL bindings are stored directly in Bytecode DSL locals
 * and cells by the lowering; nothing here selects or recreates them.
 */
final class ProtosFrameLocalBindings {
    private ProtosFrameLocalBindings() {}

    public static final class ResolvedLexicalWriteTarget {
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

    /** The generic String-keyed write target of {@code name}, or the slot-not-found Error. */
    static ResolvedLexicalWriteTarget resolveWritableLexicalTarget(
            ProtosActivation activation, String name) {
        ResolvedLexicalWriteTarget selected = selectByNameOrNull(activation, name);
        if (selected == null) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newSlotNotFound(activation));
        }
        return selected;
    }

    /**
     * TEST009-K: the String-keyed destination walk of a NAME assignment is a
     * host boundary, like {@link ProtosLexicalFallback#readByName}. I115:
     * LOCAL and CELL bindings are assigned directly and never reach it.
     */
    @TruffleBoundary
    private static ResolvedLexicalWriteTarget selectByNameOrNull(
            ProtosActivation activation,
            String name) {
        if (activation.currentContextHasLocalSlotForRuntime(name)) {
            return ResolvedLexicalWriteTarget.currentContext();
        }

        for (ProtosLexicalEnvironment lexical =
                        activation.capturedLexicalEnvironmentForRuntime();
                lexical != null;
                lexical = lexical.outer()) {
            if (lexical.hasLocalSlotForRuntime(name)) {
                return ResolvedLexicalWriteTarget.object(
                        lexical.context());
            }
        }

        if (activation.receiver()
                        instanceof ProtosObjectValue ordinaryReceiver
                && ordinaryReceiver.hasLocalSlot(name)) {
            return ResolvedLexicalWriteTarget.object(
                    ordinaryReceiver);
        }

        return null;
    }

    /** Assigns {@code value} through a resolved generic write target. */
    static Object assignResolvedLexicalTarget(
            ProtosActivation activation,
            ResolvedLexicalWriteTarget destination,
            String name,
            Object value) {
        try {
            if (destination.currentContext) {
                activation.assignCurrentLocalSlotForRuntime(
                        name,
                        value);
            } else {
                destination.object.assignLocalSlot(
                        name,
                        value);
            }
        } catch (IllegalStateException invalidMutation) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
        return value;
    }
}
