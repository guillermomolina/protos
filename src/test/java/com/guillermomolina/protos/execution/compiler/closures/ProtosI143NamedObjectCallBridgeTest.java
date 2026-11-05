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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosPreparedCalls.OrdinarySourceCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedArgumentVector;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedStandardObjectCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedStandardObjectConstruct;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * I143 / D237 / D239-C1: actual standard Object.call forwarding of evaluated
 * P,K, through standard Object.construct to the selected init.
 */
class ProtosI143NamedObjectCallBridgeTest {

    @Test
    void copiedStandardCallForwardsNamesToClosureDefaults()
            throws Exception {
        ProtosActivation caller = moduleActivation();
        ProtosClosureValue target =
                closure("(a: true, b: false) => a", caller);

        target.createLocalSlot("call", standardCallCopy());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("b", ProtosBooleanValue.TRUE);

        Object result = ProtosTestExecutionSupport.callEntered(
                () -> invokeStandard(
                        ProtosBytecodeRootNode.PrepareClosureCallVector
                                .perform(false, target, caller, supplied)));

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void forwardingPacketIsCopiedAtPreparation() throws Exception {
        ProtosActivation caller = moduleActivation();
        ProtosClosureValue target =
                closure("(a: true, b: false) => a", caller);

        target.createLocalSlot("call", standardCallCopy());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("b", ProtosBooleanValue.TRUE);

        Object result = ProtosTestExecutionSupport.callEntered(() -> {
            PreparedClosureCall prepared =
                    ProtosBytecodeRootNode.PrepareClosureCallVector
                            .perform(false, target, caller, supplied);

            supplied.appendNamed("unknown", ProtosBooleanValue.FALSE);

            return invokeStandard(prepared);
        });

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void customCallBindsOwnFormalsWithoutForwarding() {
        ProtosActivation caller = moduleActivation();
        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        receiver.createLocalSlot(
                "call", closure("(value) => value", caller));

        ProtosObjectValue argument =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("value", argument);

        Object result = ProtosTestExecutionSupport.callEntered(
                () -> enterSource(
                        ProtosBytecodeRootNode.PrepareClosureCallVector
                                .perform(false, receiver, caller, supplied)));

        assertSame(argument, result);
    }

    @Test
    void customCallRejectsUndeclaredName() {
        ProtosActivation caller = moduleActivation();
        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        receiver.createLocalSlot(
                "call", closure("(value) => value", caller));

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("unknown", ProtosBooleanValue.TRUE);

        ProtosSignalException failure = assertThrows(
                ProtosSignalException.class,
                () -> ProtosBytecodeRootNode.PrepareClosureCallVector
                        .perform(false, receiver, caller, supplied));

        assertSame(
                caller.prelude().orElseThrow().errorPrototype(),
                failure.error().parent().orElseThrow());
    }

    @Test
    void copiedStandardCallForwardsToSelectedInit()
            throws Exception {
        ProtosActivation caller = moduleActivation();
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        prototype.createLocalSlot("call", standardCallCopy());
        prototype.createLocalSlot("construct", standardConstructCopy());
        prototype.createLocalSlot(
                "init",
                closure("(x: false, y: false) => y", caller));

        ProtosObjectValue argument =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("y", argument);

        Object result = ProtosTestExecutionSupport.callEntered(() -> {
            PreparedClosureCall prepared =
                    ProtosBytecodeRootNode.PrepareClosureCallVector
                            .perform(false, prototype, caller, supplied);

            assertTrue(prepared.isStructuredObjectCall());

            PreparedStandardObjectCall standard =
                    prepared.prepareStructuredObjectCall();

            PreparedClosureCall constructCall = standard.child();
            assertTrue(constructCall.isStructuredObjectConstruct());
            assertSame(prototype, constructCall.activation().receiver());
            assertSame(
                    prototype,
                    constructCall.activation().methodHome().orElseThrow());

            PreparedStandardObjectConstruct construct =
                    constructCall.prepareStructuredObjectConstruct();
            assertSame(
                    prototype,
                    construct.child().activation()
                            .methodHome().orElseThrow());

            Object initResult = enterSource(construct.child());
            assertSame(argument, initResult);

            return prepared.finish(
                    standard.finish(
                            constructCall.finish(construct.finish(initResult))));
        });

        ProtosObjectValue instance = assertInstanceOf(
                ProtosObjectValue.class, result);

        assertSame(prototype, instance.parent().orElseThrow());
    }

    @Test
    void copiedStandardCallWorksThroughDifferentSelector()
            throws Exception {
        ProtosActivation caller = moduleActivation();
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        prototype.createLocalSlot("factory", standardCallCopy());
        prototype.createLocalSlot("construct", standardConstructCopy());
        prototype.createLocalSlot(
                "init",
                closure("(value) => value", caller));

        ProtosObjectValue argument =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("value", argument);

        Object result = ProtosTestExecutionSupport.callEntered(
                () -> invokeStandard(
                        ProtosBytecodeRootNode.PrepareSendVector.perform(
                                prototype, "factory", caller, supplied)));

        ProtosObjectValue instance = assertInstanceOf(
                ProtosObjectValue.class, result);

        assertSame(prototype, instance.parent().orElseThrow());
    }

    @Test
    void selectedInitRejectsUnknownNameBeforeItsBody()
            throws Exception {
        ProtosActivation caller = moduleActivation();
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        prototype.createLocalSlot("call", standardCallCopy());
        prototype.createLocalSlot("construct", standardConstructCopy());
        prototype.createLocalSlot(
                "init", closure("(expected) => expected", caller));

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("unknown", ProtosBooleanValue.TRUE);

        ProtosSignalException failure =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosTestExecutionSupport.callEntered(() -> {
                            PreparedClosureCall prepared =
                                    ProtosBytecodeRootNode.PrepareClosureCallVector
                                            .perform(
                                                    false, prototype, caller, supplied);
                            prepared.prepareStructuredObjectCall()
                                    .child()
                                    .prepareStructuredObjectConstruct();
                            return null;
                        }));

        assertSame(
                caller.prelude().orElseThrow().errorPrototype(),
                failure.error().parent().orElseThrow());
    }

    @Test
    void unrelatedNativeCallDoesNotAcquireNamedSignature() {
        ProtosActivation caller = moduleActivation();
        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        receiver.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> ProtosBooleanValue.TRUE));

        PreparedArgumentVector supplied = new PreparedArgumentVector();
        supplied.appendNamed("supplied", ProtosBooleanValue.FALSE);

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosBytecodeRootNode.PrepareClosureCallVector
                        .perform(false, receiver, caller, supplied));
    }

    private static Object invokeStandard(PreparedClosureCall prepared) {
        assertTrue(prepared.isStructuredObjectCall());

        PreparedStandardObjectCall standard =
                prepared.prepareStructuredObjectCall();

        PreparedClosureCall child = standard.child();
        Object result;
        if (child.isStructuredObjectConstruct()) {
            PreparedStandardObjectConstruct construct =
                    child.prepareStructuredObjectConstruct();
            Object initResult = enterSource(construct.child());
            result = child.finish(construct.finish(initResult));
        } else {
            result = enterSource(child);
        }
        return prepared.finish(standard.finish(result));
    }

    private static Object enterSource(PreparedClosureCall prepared) {
        assertFalse(prepared.isNative());
        Object result = ProtosBytecodeRootNode.EnterClosureCall
                .ordinaryIndirect(
                        assertInstanceOf(OrdinarySourceCall.class, prepared),
                        IndirectCallNode.create());
        return prepared.finish(result);
    }

    private static ProtosClosureValue standardCallCopy()
            throws Exception {
        Field field = ProtosStandardObjectProtocol.class
                .getDeclaredField("STANDARD_CALL_BODY");
        field.setAccessible(true);

        return ProtosClosureValue.nativeClosure(
                (ProtosNativeClosureBody) field.get(null));
    }

    private static ProtosClosureValue standardConstructCopy()
            throws Exception {
        Field field = ProtosStandardObjectProtocol.class
                .getDeclaredField("STANDARD_CONSTRUCT_BODY");
        field.setAccessible(true);

        return ProtosClosureValue.nativeClosure(
                (ProtosNativeClosureBody) field.get(null));
    }

    private static ProtosClosureValue closure(
            String source, ProtosActivation caller) {
        return assertInstanceOf(
                ProtosClosureValue.class,
                ProtosTestExecutionSupport.evaluate(
                        "i143-named-object-call.protos",
                        source,
                        caller));
    }

    private static ProtosActivation moduleActivation() {
        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings =
                new ProtosObjectValue(contextPrototype);
        ProtosObjectValue error =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot("Error", error);
        bindings.createLocalSlot(
                "SlotNotFound", new ProtosObjectValue(error));
        bindings.createLocalSlot(
                "Array",
                new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();

        return new ProtosPrelude(
                bindings, contextPrototype).newModuleActivation();
    }
}
