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

import com.guillermomolina.protos.execution.ProtosNamedArgumentAssociation.SourceBinding;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedArgumentVector;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.Objects;

/**
 * I143 / D237: preparation of an already selected source Closure.
 *
 * <p>The caller owns ordinary D013 lookup, including call overrides,
 * inherited method selection, and super lookup. This helper must never
 * select a Closure by name or infer formals from the source receiver.
 *
 * <p>Only source implementations with successfully associated names
 * enter this path. Actual standard Object.call forwarding is a separate
 * selected-implementation protocol; it is not silently converted to
 * ordinary named method binding.
 *
 * <p>Positional-only PLAT060-B5 calls do not use this class.
 */
final class ProtosNamedCallPreparation {
    private ProtosNamedCallPreparation() {}

    /**
     * Validate the entire evaluated packet against the actual source
     * implementation before creating its activation or running defaults.
     *
     * <p>Null from the pure classifier means a collision, unknown label,
     * unsupported native signature, or a forwarding implementation that
     * belongs to the separate standard Object.call bridge.
     */
    private static SourceBinding requireSourceBinding(
            ProtosClosureValue selected,
            PreparedArgumentVector supplied,
            ProtosActivation caller) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(caller, "caller");

        if (!supplied.hasNamedArguments()) {
            throw new IllegalArgumentException(
                    "source named preparation requires a name-bearing packet");
        }

        var classified =
                ProtosNamedArgumentAssociation
                        .classifySelectedImplementation(
                                selected, supplied);

        if (!(classified instanceof SourceBinding binding)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }

        return binding;
    }

    /**
     * Direct invocation of the actual selected source Closure.
     *
     * <p>Captures, semantic receiver, methodHome, lexical environment
     * and return-home ownership come from this exact Closure.
     */
    static ProtosActivation activationForSelectedDirectSource(
            ProtosClosureValue selected,
            ProtosActivation caller,
            PreparedArgumentVector supplied) {
        SourceBinding binding =
                requireSourceBinding(selected, supplied, caller);

        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(
                        selected,
                        binding.association()
                                .boundSuppliedArgumentsForRuntime(),
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain());

        activation.installNamedParameterPresenceForRuntime(
                binding.association().parameterPresenceForRuntime());

        ProtosSendPreparation.attachTaskOrInheritDynamicControlState(
                activation, caller);

        return activation;
    }

    /**
     * Invocation of the selected ordinary source method.
     *
     * <p>The passed receiver and methodHome are the actual D013
     * selection's values, not statically reconstructed metadata.
     */
    static ProtosActivation activationForSelectedSourceMethod(
            ProtosClosureValue selected,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosActivation caller,
            PreparedArgumentVector supplied) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(methodHome, "methodHome");

        SourceBinding binding =
                requireSourceBinding(selected, supplied, caller);

        ProtosActivation activation =
                ProtosActivation.forImmediateMethodInvocation(
                        selected,
                        binding.association()
                                .boundSuppliedArgumentsForRuntime(),
                        receiver,
                        methodHome,
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain());

        activation.installNamedParameterPresenceForRuntime(
                binding.association().parameterPresenceForRuntime());

        ProtosSendPreparation.attachTaskOrInheritDynamicControlState(
                activation, caller);

        return activation;
    }

    /**
     * Prepared source call after successful dynamic selection and
     * association. Reuses ordinary composed-call execution.
     */
    static PreparedClosureCall prepareSelectedDirectSource(
            ProtosClosureValue selected,
            ProtosActivation caller,
            PreparedArgumentVector supplied) {
        ProtosSendPreparation.rejectComposedInvocationProjection(
                selected);

        ProtosActivation activation =
                activationForSelectedDirectSource(
                        selected, caller, supplied);

        return ProtosSendPreparation
                .finishPreparingComposedCallByImplementation(
                        selected,
                        activation.suppliedArgumentsForRuntime(),
                        activation);
    }

    /**
     * Prepared selected source method, preserving its actual receiver
     * and method home.
     */
    static PreparedClosureCall prepareSelectedSourceMethod(
            ProtosClosureValue selected,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosActivation caller,
            PreparedArgumentVector supplied) {
        ProtosSendPreparation.rejectComposedInvocationProjection(
                selected);

        ProtosActivation activation =
                activationForSelectedSourceMethod(
                        selected, receiver, methodHome, caller, supplied);

        return ProtosSendPreparation
                .finishPreparingComposedCallByImplementation(
                        selected,
                        activation.suppliedArgumentsForRuntime(),
                        activation);
    }
}
