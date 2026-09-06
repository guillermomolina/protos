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

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.Objects;

/**
 * Inert terminal result of one exact Protos execution boundary.
 *
 * <p>This value deliberately carries no Process, Actor, task, scheduler, worker, retry, test-case
 * or reporting identity. Higher-level tooling may interpret a terminal semantic result, while
 * infrastructure/runtime failures outside Protos execution remain ordinary host failures rather
 * than fabricated Protos Errors.
 */
public record ProtosExecutionOutcome(
        State state,
        Object value,
        ProtosObjectValue error) {

    public enum State {
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public ProtosExecutionOutcome {
        Objects.requireNonNull(state, "state");
        switch (state) {
            case COMPLETED -> {
                Objects.requireNonNull(value, "completed execution value");
                if (error != null) {
                    throw new IllegalArgumentException(
                            "completed execution cannot carry an error");
                }
            }
            case FAILED -> {
                if (value != null) {
                    throw new IllegalArgumentException(
                            "failed execution cannot carry a completed value");
                }
                Objects.requireNonNull(error, "failed execution error");
            }
            case CANCELLED -> {
                if (value != null || error != null) {
                    throw new IllegalArgumentException(
                            "cancelled execution cannot carry value/error data");
                }
            }
        }
    }

    public static ProtosExecutionOutcome completed(Object value) {
        return new ProtosExecutionOutcome(
                State.COMPLETED,
                Objects.requireNonNull(value, "value"),
                null);
    }

    public static ProtosExecutionOutcome failed(ProtosObjectValue error) {
        return new ProtosExecutionOutcome(
                State.FAILED,
                null,
                Objects.requireNonNull(error, "error"));
    }

    public static ProtosExecutionOutcome cancelled() {
        return new ProtosExecutionOutcome(State.CANCELLED, null, null);
    }
}
