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

import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.ProtosValueLookup.GuardedSlotSelection;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * I115-H7 EXPERIMENTAL, proxied as the {@code GetMethod} operation (GraalPy
 * {@code GetMethod.doFastPath} shape): the selection half of an ordinary
 * one-argument send. The receiver is evaluated once by the caller bytecode;
 * the selector is a constant operand. The result is a compact {@link
 * Method} descriptor consumed by {@code CallBinaryMethod}; no prepared call
 * is created.
 *
 * <p>Caller protocol (I115-H7-R1): the caller reference and the caller
 * prelude are read exactly as every other send selection of a compact root
 * reads them ({@code CurrentActivation(callerReference)} and {@code
 * InvokeClosureOneTail#admittedCallerPrelude}): a published activation
 * answers directly; an unpublished invocation whose inherited prelude was
 * not transported registers this root's {@code NEEDS_CALLER_PRELUDE} once,
 * so its callers transport it from then on. Nothing is published here.
 *
 * <p>{@link #doCached} is the monomorphic/polymorphic selection cache (at
 * most three entries), guarded by receiver identity, the caller prelude and
 * the selection's stability {@link com.oracle.truffle.api.Assumption}. It
 * reads the current value of the cached physical slot location ({@link
 * GuardedSlotSelection#currentValue()}), so a reassignment of the same slot
 * is observed and a removal or recreation invalidates the entry. {@link
 * #doGeneric} is the separated fallback with the complete lookup.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
@ConstantOperand(type = TruffleString.class, name = "selector")
abstract class ProtosExperimentalGetMethodNode extends Node {

    /**
     * I115-H7: the immutable selection result (GraalPy's bound-descriptor
     * stack pair). {@code callable} is the selected slot value, or null when
     * no slot was selected; {@code home} is the selected owner, or null.
     * {@code selector} lets the generic call tier repeat the ordinary send.
     * {@code caller} is this root's caller reference (its published
     * activation or its own B5 array) and {@code callerPrelude} the prelude
     * the selection used; the call half builds the callee's caller slot from
     * them through the existing B5 producer contract.
     */
    record Method(
            Object callable,
            Object receiver,
            ProtosObjectValue home,
            String selector,
            Object caller,
            ProtosPrelude callerPrelude) {}

    abstract Object execute(VirtualFrame frame, TruffleString selector, Object receiver);

    @Specialization(
            excludeForUncached = true,
            guards = {
                "receiver == cachedReceiver",
                "prelude == cachedPrelude",
                "cachedSelection != null"
            },
            assumptions = "cachedSelection.stability()",
            limit = "3")
    static Method doCached(
            VirtualFrame frame,
            TruffleString selector,
            Object receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Bind("callerReference(frame)") Object caller,
            @Bind("callerPrelude(root, caller)") ProtosPrelude prelude,
            @Cached("receiver") Object cachedReceiver,
            @Cached("prelude") ProtosPrelude cachedPrelude,
            @Cached("selector.toJavaStringUncached()") String cachedSelector,
            @Cached("selectOrNull(receiver, cachedSelector, prelude)")
                    GuardedSlotSelection cachedSelection) {
        return new Method(
                cachedSelection.currentValue(),
                receiver,
                cachedSelection.home(),
                cachedSelector,
                caller,
                prelude);
    }

    @Specialization(replaces = "doCached")
    @InliningCutoff
    static Method doGeneric(
            VirtualFrame frame,
            TruffleString selector,
            Object receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        Object caller = callerReference(frame);
        ProtosPrelude prelude = callerPrelude(root, caller);
        return selectGeneric(
                receiver, selector.toJavaStringUncached(), caller, prelude);
    }

    /** This root's caller reference, unpublished when still compact. */
    @NonIdempotent
    static Object callerReference(VirtualFrame frame) {
        return ProtosBytecodeRootNode.InvokeClosureOneTail.callerReference(frame);
    }

    /** The caller prelude by the admitted B5 accessors, registering the need once. */
    static ProtosPrelude callerPrelude(ProtosBytecodeRootNode root, Object caller) {
        return ProtosBytecodeRootNode.InvokeClosureOneTail.admittedCallerPrelude(root, caller);
    }

    /* Installation only: the guarded physical selection, or null. */
    @TruffleBoundary
    static GuardedSlotSelection selectOrNull(
            Object receiver, String selector, ProtosPrelude prelude) {
        if (!(receiver instanceof ProtosObjectValue)) {
            return null;
        }
        try {
            return ProtosValueLookup.lookupGuardedSlotSelection(receiver, selector, prelude);
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            return null;
        }
    }

    /* The complete lookup; a missing or unsupported selection yields no callable. */
    @TruffleBoundary
    static Method selectGeneric(
            Object receiver, String selector, Object caller, ProtosPrelude prelude) {
        java.util.Optional<ProtosSlotLookupResult> selected;
        try {
            selected = ProtosValueLookup.lookup(
                    ProtosNumericValueSupport.guestValue(receiver), selector, prelude);
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            selected = java.util.Optional.empty();
        }
        if (selected.isEmpty()) {
            return new Method(null, receiver, null, selector, caller, prelude);
        }
        ProtosSlotLookupResult result = selected.orElseThrow();
        return new Method(result.value(), receiver, result.home(), selector, caller, prelude);
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
