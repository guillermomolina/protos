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
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.ControlFlowException;
import java.util.Objects;

/**
 * I115 test support: entries into a source Closure's CallTarget with exactly
 * the B5 header {@link ProtosFunctionCallDispatchers} writes (Closure,
 * function marker or receiver/methodHome, caller reference as given, and the
 * ReturnHome the dispatcher selects), so tests can inspect the header before
 * and after the callee runs. Rich entries use the published-activation header.
 */
public final class ProtosDispatchTestSupport {
    private ProtosDispatchTestSupport() {}

    /** A CallTarget and the exact frame arguments it is entered with. */
    public record DirectEntry(RootCallTarget target, Object[] targetArguments) {
        public DirectEntry {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(targetArguments, "targetArguments");
        }
    }

    /** The effective CallTarget of {@code closure} in the entered Context. */
    public static RootCallTarget target(ProtosClosureValue closure) {
        return ProtosClosureCallPreparation.functionCallTarget(
                closure, ProtosLanguageContext.currentIfEnteredForRuntime());
    }

    /** A function call of {@code closure} by {@code caller} (activation or caller header). */
    public static DirectEntry direct(ProtosClosureValue closure, Object caller, Object... supplied) {
        RootCallTarget target = target(closure);
        Object[] arguments = ProtosFrameArguments.functionCallArguments(supplied);
        ProtosFrameArguments.completeDispatchedHeader(arguments, closure, caller, homeFor(closure, target, caller));
        return new DirectEntry(target, arguments);
    }

    /** A selected method call, with receiver and methodHome in the header. */
    public static DirectEntry method(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            Object caller,
            Object... supplied) {
        RootCallTarget target = target(closure);
        Object[] arguments = ProtosFrameArguments.methodCallArguments(receiver, methodHome, supplied);
        ProtosFrameArguments.completeDispatchedHeader(arguments, closure, caller, homeFor(closure, target, caller));
        return new DirectEntry(target, arguments);
    }

    /** An entry whose activation is already materialized and published. */
    public static DirectEntry rich(RootCallTarget target, ProtosActivation activation) {
        return new DirectEntry(target, ProtosFrameArguments.publishedInvocation(activation, target));
    }

    /** Enters the CallTarget; the result is projected to the guest domain. */
    public static Object enter(DirectEntry entry) {
        return ProtosNumericValueSupport.guestValue(
                enterRaw(entry));
    }

    /** As {@link #enter}, without projecting the raw callee result. */
    public static Object enterRaw(DirectEntry entry) {
        ProtosReturnHome owned =
                ProtosFrameArguments.ownedReturnHomeOrNull(entry.targetArguments());
        if (owned == null) {
            return entry.target().call(entry.targetArguments());
        }
        try {
            return entry.target().call(entry.targetArguments());
        } catch (ControlFlowException transfer) {
            return ProtosFunctionCallDispatchers.completeOwnedTransfer(owned, transfer);
        }
    }

    /* The dispatcher's ReturnHome selection (ProtosFunctionCallDispatchers). */
    private static ProtosReturnHome homeFor(
            ProtosClosureValue closure, RootCallTarget target, Object caller) {
        ProtosBytecodeRootNode root = ProtosFunctionCallDispatchers.calleeRootOrNull(target);
        ProtosReturnHome home = closure.invocationReturnHomeUnderTemplateProofForRuntime(
                root != null && root.returnHomeProvenUnobservable());
        var task = caller instanceof ProtosActivation activation
                ? activation.taskForRuntime()
                : caller instanceof Object[] arguments
                        ? ProtosFrameArguments.admittedCompactTask(arguments) : null;
        ProtosFrameArguments.bindOwnedReturnHome(closure, home, task);
        return home;
    }
}
