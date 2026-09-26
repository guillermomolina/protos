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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosReturnHome;
import com.oracle.truffle.api.frame.Frame;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class ProtosFrameArguments {
    /*
     * PLAT040 / I072-B compact ordinary source-call ABI.
     *
     * Captured lexical state and executable identity remain owned by the
     * Closure. Caller-owned module/domain/control provenance is referenced,
     * rather than copied into another rich per-call aggregate. User arguments
     * follow the fixed runtime header directly, as in mature Truffle frame
     * argument conventions.
     */
    private static final int CLOSURE_INDEX = 0;
    private static final int RECEIVER_INDEX = 1;
    private static final int METHOD_HOME_INDEX = 2;
    private static final int CALLER_INDEX = 3;
    private static final int RETURN_HOME_INDEX = 4;
    private static final int USER_ARGUMENT_OFFSET = 5;

    private ProtosFrameArguments() {}

    static Object[] compactImmediateMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosActivation caller,
            Object[] supplied) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(methodHome, "methodHome");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(supplied, "supplied");

        ProtosReturnHome returnHome =
                closure.returnHome().orElseGet(ProtosReturnHome::new);
        Object[] arguments =
                new Object[USER_ARGUMENT_OFFSET + supplied.length];
        arguments[CLOSURE_INDEX] = closure;
        arguments[RECEIVER_INDEX] = receiver;
        arguments[METHOD_HOME_INDEX] = methodHome;
        arguments[CALLER_INDEX] = caller;
        arguments[RETURN_HOME_INDEX] = returnHome;
        System.arraycopy(
                supplied,
                0,
                arguments,
                USER_ARGUMENT_OFFSET,
                supplied.length);
        return arguments;
    }

    static boolean hasActivation(Frame frame) {
        if (frame == null) {
            return false;
        }
        Object[] arguments = frame.getArguments();
        return arguments.length > 0
                && (arguments[0] instanceof ProtosActivation
                        || isCompactImmediateMethodCall(arguments));
    }

    static ProtosActivation activation(Frame frame) {
        if (frame == null) {
            throw new IllegalStateException(
                    "Execution node requires Protos frame arguments");
        }

        Object[] arguments = frame.getArguments();
        if (arguments.length > 0
                && arguments[0] instanceof ProtosActivation activation) {
            return activation;
        }
        if (!isCompactImmediateMethodCall(arguments)) {
            throw new IllegalStateException(
                    "Execution node requires a Protos activation or compact source-call ABI");
        }

        ProtosClosureValue closure =
                (ProtosClosureValue) arguments[CLOSURE_INDEX];
        ProtosActivation caller =
                (ProtosActivation) arguments[CALLER_INDEX];
        ProtosReturnHome returnHome =
                (ProtosReturnHome) arguments[RETURN_HOME_INDEX];
        List<Object> supplied =
                Arrays.asList(arguments)
                        .subList(USER_ARGUMENT_OFFSET, arguments.length);

        ProtosActivation materialized =
                ProtosActivation.forImmediateMethodInvocationWithReturnHomeForRuntime(
                        closure,
                        supplied,
                        arguments[RECEIVER_INDEX],
                        (ProtosObjectValue) arguments[METHOD_HOME_INDEX],
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain(),
                        returnHome);

        if (caller.task().isPresent()) {
            materialized.attachTask(caller.task().orElseThrow());
        } else {
            materialized.inheritDynamicControlState(caller);
        }

        /*
         * Debugger/NodeLibrary observation may request the activation before
         * the semantic helper operation does. Publish the exact materialized
         * activation back into this frame so every observer and the helper
         * share one fresh guest execution-context identity.
         */
        arguments[CLOSURE_INDEX] = materialized;
        return materialized;
    }

    static ProtosReturnHome compactReturnHome(Object[] arguments) {
        requireCompactImmediateMethodCall(arguments);
        return (ProtosReturnHome) arguments[RETURN_HOME_INDEX];
    }

    static boolean compactOwnsReturnHome(Object[] arguments) {
        requireCompactImmediateMethodCall(arguments);
        return ((ProtosClosureValue) arguments[CLOSURE_INDEX])
                .returnHome()
                .isEmpty();
    }

    static ProtosActivation compactCaller(Object[] arguments) {
        if (arguments == null
                || arguments.length < USER_ARGUMENT_OFFSET
                || !(arguments[CALLER_INDEX] instanceof ProtosActivation caller)) {
            throw new IllegalStateException(
                    "Invalid compact source-call caller frame argument");
        }
        return caller;
    }

    private static boolean isCompactImmediateMethodCall(Object[] arguments) {
        return arguments != null
                && arguments.length >= USER_ARGUMENT_OFFSET
                && arguments[CLOSURE_INDEX] instanceof ProtosClosureValue
                && arguments[RECEIVER_INDEX] != null
                && arguments[METHOD_HOME_INDEX] instanceof ProtosObjectValue
                && arguments[CALLER_INDEX] instanceof ProtosActivation
                && arguments[RETURN_HOME_INDEX] instanceof ProtosReturnHome;
    }

    private static void requireCompactImmediateMethodCall(
            Object[] arguments) {
        if (!isCompactImmediateMethodCall(arguments)) {
            throw new IllegalStateException(
                    "Invalid compact ordinary source-call frame arguments");
        }
    }
}
