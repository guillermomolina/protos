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


/**
 * I113: the result classifications an {@code IsResultKind} instruction tests.
 * The builder fixes one constant per instruction; each constant carries its
 * own predicate body, so the call on the constant receiver devirtualizes
 * under partial evaluation.
 */
public enum ProtosResultKind {

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
