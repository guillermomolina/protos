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
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.ProtosValueLookup.GuardedSlotSelection;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * I115 (GraalPy {@code GetMethod}), proxied as the {@code GetMethod}
 * operation: the selection half of every ordinary send. The receiver was
 * evaluated once by the caller bytecode; the selector is a constant operand.
 * Ordinary selection returns a {@link ProtosBoundMethod} carrying the
 * selected Closure, receiver and methodHome. Direct cached selection returns
 * the raw Closure; D188 foreign member selection returns its read callable.
 * Nothing is prepared or executed here.
 *
 * <p>{@link #doCached} is the selection cache (at most three entries),
 * guarded by receiver identity and the selection's stability assumption; it
 * reads the current value of the cached slot location, so a reassignment is
 * observed and a removal or recreation invalidates the entry. A cached slot
 * that no longer holds a Closure falls to {@link #doGeneric}, which performs
 * the complete lookup and raises its Errors.
 *
 * <p>RECORDED DIVERGENCE (I115-H7-R3): the cached selection is installed
 * with a {@code null} prelude and is not re-validated against a later change
 * of the caller's prelude.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = true)
@ConstantOperand(type = TruffleString.class, name = "selector")
@ConstantOperand(type = boolean.class, name = "direct")
abstract class ProtosExperimentalGetMethodNode extends Node {

    abstract Object execute(
            VirtualFrame frame, TruffleString selector,
            boolean direct, Object receiver);

    @Specialization(
            excludeForUncached = true,
            guards = {
                "receiver == cachedReceiver",
                "cachedSelection != null",
                "isClosure(cachedSelection.currentValue(getNode))"
            },
            assumptions = "cachedSelection.stability()",
            limit = "3")
    @ForceQuickening
    static Object doCached(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            Object receiver,
            @Cached("receiver") Object cachedReceiver,
            @Cached("selectOrNull(receiver, selector)")
                    GuardedSlotSelection cachedSelection,
            @Cached com.oracle.truffle.api.object.DynamicObject.GetNode getNode) {
        ProtosClosureValue selected =
                (ProtosClosureValue) cachedSelection.currentValue(getNode);
        if (direct) {
            // I115 divergence: the receiver travels separately; the
            // inherited method's exact original methodHome is not retained.
            return selected;
        }
        return new ProtosBoundMethod(
                selected, receiver, cachedSelection.home());
    }

    @Specialization(replaces = "doCached")
    @InliningCutoff
    static Object doGeneric(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            Object receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        Object caller = frame.getArguments();
        return select(
                ProtosNumericValueSupport.guestValue(receiver),
                selector.toJavaStringUncached(),
                root,
                caller);
    }

    static boolean isClosure(Object value) {
        return value instanceof ProtosClosureValue;
    }

    /* Installation only: the guarded physical selection without a prelude, or null. */
    @TruffleBoundary
    static GuardedSlotSelection selectOrNull(Object receiver, TruffleString selector) {
        if (!(receiver instanceof ProtosObjectValue)) {
            return null;
        }
        try {
            return ProtosValueLookup.lookupGuardedSlotSelection(
                    receiver, selector.toJavaStringUncached(), null);
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            return null;
        }
    }

    /**
     * The complete D013 send selection: a missing slot is SlotNotFound, an
     * unsupported representation or a non-Closure selection is Error, a D188
     * foreign member is its read value.
     */
    @TruffleBoundary
    static Object select(
            Object receiver, String selector, ProtosBytecodeRootNode root, Object caller) {
        ProtosPrelude prelude = ProtosFunctionCallDispatchers.admittedCallerPrelude(root, caller);
        ProtosSlotLookupResult selected;
        try {
            var found = ProtosValueLookup.lookup(receiver, selector, prelude);
            if (found.isEmpty()) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newSlotNotFound(exactCaller(root, caller)));
            }
            selected = found.orElseThrow();
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(exactCaller(root, caller)));
        }
        return bind(receiver, selected, root, caller);
    }

    /**
     * The super-send selection (GraalPy {@code super().method}): the lookup
     * starts at the parent of the caller's methodHome and the selected method
     * is bound to the caller's receiver.
     */
    @TruffleBoundary
    static Object selectSuper(String selector, ProtosBytecodeRootNode root, Object caller) {
        ProtosActivation activation = exactCaller(root, caller);
        ProtosObjectValue methodHome =
                activation.methodHome()
                        .orElseThrow(
                                () -> new ProtosSignalException(
                                        ProtosCoreErrors.newInvalidSuper(activation)));
        Object lookupOrigin =
                methodHome.parent()
                        .orElseThrow(
                                () -> new ProtosSignalException(
                                        ProtosCoreErrors.newSlotNotFound(activation)));
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(lookupOrigin, selector, activation.preludeOrNullForRuntime())
                            .orElseThrow(
                                    () -> new ProtosSignalException(
                                            ProtosCoreErrors.newSlotNotFound(activation)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return new ProtosBoundMethod(closure, activation.receiver(), selected.home());
    }

    private static Object bind(
            Object receiver,
            ProtosSlotLookupResult selected,
            ProtosBytecodeRootNode root,
            Object caller) {
        if (ProtosForeignProjectedOperations.isForeignMemberSelection(selected)) {
            // D188: the read foreign member is invoked as an ordinary callable.
            return selected.value();
        }
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(exactCaller(root, caller)));
        }
        return new ProtosBoundMethod(closure, receiver, selected.home());
    }

    private static ProtosActivation exactCaller(ProtosBytecodeRootNode root, Object caller) {
        return ProtosFunctionCallDispatchers.exactCaller(root, caller);
    }

    /*
     * I115-H7-R1: the Protos root executing this operation. In a proxied
     * node the default root bind is typed Node, so it is resolved from the
     * enclosing BytecodeNode.
     */
    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}
