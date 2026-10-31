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
 * I115-H7 EXPERIMENTAL, proxied as the {@code GetMethod} operation (GraalPy
 * {@code GetMethod.doFastPath} shape): the selection half of an ordinary
 * one-argument send. The receiver is evaluated once by the caller bytecode
 * and kept on the stack as a separate operand of {@code CallBinaryMethod};
 * the selector is a constant operand. I115-H7-R3: the result is the selected
 * slot value itself (or {@code null} when nothing was selected), never a
 * descriptor; no prepared call is created.
 *
 * <p>{@link #doCached} is the monomorphic/polymorphic selection cache (at
 * most three entries), guarded by receiver identity and the selection's
 * stability {@link com.oracle.truffle.api.Assumption}. It reads the current
 * value of the cached physical slot location ({@link
 * GuardedSlotSelection#currentValue()}), so a reassignment of the same slot
 * is observed and a removal or recreation invalidates the entry. It reads
 * neither the caller reference nor the caller prelude. {@link #doGeneric} is
 * the separated fallback with the complete lookup.
 *
 * <p>RECORDED DIVERGENCE (I115-H7-R3): the cached selection is installed
 * with a {@code null} prelude and is not re-validated against a later change
 * of the caller's prelude.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
@ConstantOperand(type = TruffleString.class, name = "selector")
abstract class ProtosExperimentalGetMethodNode extends Node {

    abstract Object execute(VirtualFrame frame, TruffleString selector, Object receiver);

    @Specialization(
            excludeForUncached = true,
            guards = {
                "receiver == cachedReceiver",
                "cachedSelection != null"
            },
            assumptions = "cachedSelection.stability()",
            limit = "3")
    @ForceQuickening
    static Object doCached(
            VirtualFrame frame,
            TruffleString selector,
            Object receiver,
            @Cached("receiver") Object cachedReceiver,
            @Cached("selectOrNull(receiver, selector)")
                    GuardedSlotSelection cachedSelection,
            @Cached com.oracle.truffle.api.object.DynamicObject.GetNode getNode) {
        return cachedSelection.currentValue(getNode);
    }

    @Specialization(replaces = "doCached")
    @InliningCutoff
    static Object doGeneric(
            VirtualFrame frame,
            TruffleString selector,
            Object receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        Object caller = ProtosBytecodeRootNode.InvokeClosureOneTail.callerReference(frame);
        ProtosPrelude prelude =
                ProtosBytecodeRootNode.InvokeClosureOneTail.admittedCallerPrelude(root, caller);
        return selectGenericWithHome(receiver, selector.toJavaStringUncached(), prelude);
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

    /* The complete lookup; a missing or unsupported selection yields null. */
    @TruffleBoundary
    static Object selectGeneric(Object receiver, String selector, ProtosPrelude prelude) {
        try {
            return ProtosValueLookup.lookup(
                            ProtosNumericValueSupport.guestValue(receiver), selector, prelude)
                    .map(ProtosSlotLookupResult::value)
                    .orElse(null);
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            return null;
        }
    }

    /* I115-H11-B3: preserve the selected home only in the generic tier. */
    @TruffleBoundary
    static Object selectGenericWithHome(Object receiver, String selector, ProtosPrelude prelude) {
        try {
            var found = ProtosValueLookup.lookup(ProtosNumericValueSupport.guestValue(receiver), selector, prelude);
            if (found.isEmpty()) return null;
            ProtosSlotLookupResult selected = found.orElseThrow();
            return receiver != null && receiver.getClass() == ProtosObjectValue.class
                    && selected.value() instanceof com.guillermomolina.protos.runtime.ProtosClosureValue
                    ? selected : selected.value();
        } catch (UnsupportedOperationException unsupported) {
            return null;
        }
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
