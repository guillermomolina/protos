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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.execution.ProtosPreparedCalls.OrdinarySourceCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedArgumentVector;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.junit.jupiter.api.Test;

/** I143: D013 dynamic source-method selection before named association. */
class ProtosI143NamedSendSelectionTest {

    @Test
    void namedSendUsesActualReceiverAndMethodHome() {
        ProtosActivation caller = moduleActivation();

        ProtosClosureValue method =
                closure("(value) => value", caller);

        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiver.createLocalSlot("execute", method);

        ProtosObjectValue argument =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("value", argument);

        Object result = ProtosTestExecutionSupport.callEntered(() -> {
            PreparedClosureCall prepared =
                    ProtosBytecodeRootNode.PrepareSendVector.perform(
                            receiver, "execute", caller, supplied);

            assertSame(receiver, prepared.activation().receiver());
            assertSame(
                    receiver,
                    prepared.activation().methodHome().orElseThrow());

            return enter(prepared);
        });

        assertSame(argument, result);
    }

    @Test
    void sourceDefaultsRunOnlyForAbsentFormals() {
        ProtosActivation caller = moduleActivation();

        ProtosClosureValue method =
                closure("(a: false, b: true, c: false) => b", caller);

        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiver.createLocalSlot("execute", method);

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("c", ProtosBooleanValue.TRUE);

        Object result = ProtosTestExecutionSupport.callEntered(
                () -> enter(
                        ProtosBytecodeRootNode.PrepareSendVector.perform(
                                receiver, "execute", caller, supplied)));

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void superUsesTheInheritedMethodNotTheCurrentOverride() {
        ProtosActivation module = moduleActivation();

        ProtosClosureValue inherited =
                closure("(target) => target", module);
        ProtosClosureValue overridden =
                closure("(other) => other", module);

        ProtosObjectValue parent =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        parent.createLocalSlot("execute", inherited);

        ProtosObjectValue home = new ProtosObjectValue(parent);
        home.createLocalSlot("execute", overridden);

        ProtosObjectValue receiver = new ProtosObjectValue(home);

        ProtosActivation caller =
                ProtosActivation.forImmediateMethodInvocation(
                        overridden,
                        java.util.List.of(),
                        receiver,
                        home,
                        module.prelude().orElseThrow(),
                        module.actorModuleState(),
                        module.currentModuleKey().orElse(null),
                        module.executionDomain());

        ProtosObjectValue argument =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("target", argument);

        Object result = ProtosTestExecutionSupport.callEntered(() -> {
            PreparedClosureCall prepared =
                    ProtosBytecodeRootNode.PrepareClosureCallVector.perform(
                            true, "execute", caller, supplied);

            assertSame(receiver, prepared.activation().receiver());
            assertSame(
                    parent,
                    prepared.activation().methodHome().orElseThrow());

            return enter(prepared);
        });

        assertSame(argument, result);
    }

    @Test
    void selectedCustomCallBindsItsOwnNames() {
        ProtosActivation caller = moduleActivation();

        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiver.createLocalSlot(
                "call",
                closure("(label) => label", caller));

        ProtosObjectValue argument =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("label", argument);

        Object result = ProtosTestExecutionSupport.callEntered(
                () -> enter(
                        ProtosBytecodeRootNode.PrepareSendVector.perform(
                                receiver, "call", caller, supplied)));

        assertSame(argument, result);
    }

    @Test
    void unknownNameFailsAgainstSelectedMethod() {
        ProtosActivation caller = moduleActivation();

        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiver.createLocalSlot(
                "execute",
                closure("(value) => value", caller));

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("unknown", ProtosBooleanValue.TRUE);

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosBytecodeRootNode.PrepareSendVector.perform(
                        receiver, "execute", caller, supplied));
    }

    @Test
    void missingSelectedMethodFailsBeforeNameAssociation() {
        ProtosActivation caller = moduleActivation();

        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("unknown", ProtosBooleanValue.TRUE);

        ProtosSignalException failure = assertThrows(
                ProtosSignalException.class,
                () -> ProtosBytecodeRootNode.PrepareSendVector.perform(
                        receiver, "noSuchMethod", caller, supplied));

        assertSame(
                ProtosCoreErrors.prototype(
                        caller,
                        ProtosCoreErrors.StandardError.SLOT_NOT_FOUND),
                failure.error().parent().orElseThrow());
    }

    private static Object enter(PreparedClosureCall prepared) {
        Object entered = ProtosBytecodeRootNode.EnterClosureCall
                .ordinaryIndirect(
                        assertInstanceOf(
                                OrdinarySourceCall.class, prepared),
                        IndirectCallNode.create());

        return prepared.finish(entered);
    }

    private static ProtosClosureValue closure(
            String source,
            ProtosActivation caller) {
        return assertInstanceOf(
                ProtosClosureValue.class,
                ProtosTestExecutionSupport.evaluate(
                        "i143-named-selection.protos", source, caller));
    }

    private static ProtosActivation moduleActivation() {
        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings =
                new ProtosObjectValue(contextPrototype);

        bindings.createLocalSlot("Context", contextPrototype);
        ProtosObjectValue error =
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject());
        bindings.createLocalSlot("Error", error);
        bindings.createLocalSlot(
                "SlotNotFound",
                new ProtosObjectValue(error));
        bindings.createLocalSlot(
                "Array",
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject()));
        bindings.freeze();

        return new ProtosPrelude(
                bindings, contextPrototype).newModuleActivation();
    }
}
