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

import com.guillermomolina.protos.execution.ProtosInlineCallbackFrameBindings.PreparedInlineLiteralCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.runtime.ProtosNullValue;

/**
 * I113: the result classifications an {@code IsResultKind} instruction tests.
 * The builder fixes one constant per instruction; each constant carries its
 * own predicate body, so the call on the constant receiver devirtualizes
 * under partial evaluation.
 */
public enum ProtosResultKind {
    /** The compact canonical Boolean selection missed. */
    COMPACT_BOOLEAN_MISS {
        @Override
        boolean matches(Object value) {
            return value == null;
        }
    },

    /** The compact canonical Boolean selection needs no callback. */
    COMPACT_BOOLEAN_NO_CALLBACK {
        @Override
        boolean matches(Object value) {
            return value == ProtosNullValue.INSTANCE;
        }
    },

    /** A prepared inline literal callback. */
    INLINE_LITERAL_CALL {
        @Override
        boolean matches(Object value) {
            return value instanceof PreparedInlineLiteralCall;
        }
    },

    /** The primitive numeric send fast path missed. */
    PRIMITIVE_NUMERIC_SEND_MISS {
        @Override
        boolean matches(Object value) {
            return value == ProtosPrimitiveNumericSendFastPathsBase.MISS;
        }
    },

    /**
     * I108-A: only a prepared invocation requires caller-side Bytecode DSL
     * control composition. Direct invocation results remain ordinary guest
     * values, without a wrapper allocation. PreparedClosureCall is an
     * internal runtime interface and cannot be produced as a guest-language
     * result.
     */
    PREPARED_CLOSURE_INVOCATION {
        @Override
        boolean matches(Object value) {
            return value instanceof PreparedClosureCall;
        }
    },

    /** The direct send fast path missed. */
    DIRECT_SEND_MISS {
        @Override
        boolean matches(Object value) {
            return value == ProtosBytecodeRootNode.TryDirectSendOne.MISS;
        }
    },

    /** A materialized captured owner frame was selected. */
    CAPTURED_OWNER_FRAME_SELECTED {
        @Override
        boolean matches(Object value) {
            return value != null;
        }
    },

    /** The BufferedWriter C-prime step gate requires the step. */
    BUFFERED_WRITER_STEP_REQUIRED {
        @Override
        boolean matches(Object value) {
            if (!(value instanceof Boolean required)) {
                throw new IllegalStateException(
                        "BufferedWriter C-prime step gate produced a non-Boolean value");
            }
            return required;
        }
    },

    /** The TextReader C-prime advance needs lower input. */
    TEXT_READER_NEED_INPUT {
        @Override
        boolean matches(Object value) {
            if (!(value instanceof ProtosTextReaderCPrimeExecution.Advance state)) {
                throw new IllegalStateException(
                        "TextReader C-prime advance produced an invalid carrier");
            }
            return state.needsInput();
        }
    },

    /**
     * A continuation or suspension the caller must compose; exactly
     * {@link ProtosBytecodeRootNode.IsContinuation#perform}.
     */
    CONTINUATION {
        @Override
        boolean matches(Object value) {
            return ProtosBytecodeRootNode.IsContinuation.perform(value);
        }
    };

    abstract boolean matches(Object value);
}
