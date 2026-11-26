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
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Invokes the behavior selected for a binary operator selector when it is
 * not the standard numeric or String operator. Both operands were already
 * evaluated, once, by the enclosing operation; selection happens only now.
 *
 * <p>Selection is the send selection of {@code GetMethod}. At a site that
 * observes few selectors, each selector owns a cached {@code GetMethod}
 * child, so its selection entries (keyed by the selector, the receiver
 * representation, its Shape or observing Prelude, and the structural
 * selection whose current slot value is read) are never shared between
 * selectors; the
 * child is executed in non-direct mode, so the selection always carries the
 * original receiver and the selected methodHome. Past that limit the
 * complete uncached selection is used. In both cases the shared
 * {@link ProtosFunctionCallDispatchers.CallNode} invokes the selection
 * exactly as an ordinary send: the caller, Prelude, ReturnHome, guest
 * Errors, non-local control transfers and suspensions are those of that
 * dispatcher. A suspended result is returned unchanged to a non-terminal
 * site, which composes it in its own bytecode; a terminal site wraps it in
 * the terminal transport carrying its call location.
 */
@GenerateUncached
@GenerateInline(false)
abstract class ProtosNumericOverloadNode extends Node {

    abstract Object execute(
            VirtualFrame frame,
            boolean terminal,
            String selector,
            Object receiver,
            Object argument,
            ProtosBytecodeRootNode root,
            BytecodeNode bytecode,
            int bytecodeIndex);

    /* Cached selection per selector, followed by the ordinary dispatcher. */
    @Specialization(
            guards = "selector == cachedSelector",
            limit = "3",
            excludeForUncached = true)
    static Object doCached(
            VirtualFrame frame,
            boolean terminal,
            String selector,
            Object receiver,
            Object argument,
            ProtosBytecodeRootNode root,
            BytecodeNode bytecode,
            int bytecodeIndex,
            @Cached("selector") String cachedSelector,
            @Cached("toTruffleString(cachedSelector)") TruffleString cachedName,
            @Cached ProtosExperimentalGetMethodNode getMethod,
            @Exclusive @Cached ProtosFunctionCallDispatchers.CallNode call) {
        Object method = getMethod.execute(
                frame, cachedName, false,
                ProtosNumericValueSupport.guestValue(receiver));
        return invoke(frame, terminal, method, argument, root, bytecode, bytecodeIndex, call);
    }

    /* The complete send selection followed by the ordinary dispatcher. */
    @Specialization(replaces = "doCached")
    @InliningCutoff
    static Object doGeneric(
            VirtualFrame frame,
            boolean terminal,
            String selector,
            Object receiver,
            Object argument,
            ProtosBytecodeRootNode root,
            BytecodeNode bytecode,
            int bytecodeIndex,
            @Exclusive @Cached ProtosFunctionCallDispatchers.CallNode call) {
        Object method = ProtosExperimentalGetMethodNode.select(
                ProtosNumericValueSupport.guestValue(receiver),
                selector,
                root,
                frame.getArguments());
        return invoke(frame, terminal, method, argument, root, bytecode, bytecodeIndex, call);
    }

    private static Object invoke(
            VirtualFrame frame,
            boolean terminal,
            Object method,
            Object argument,
            ProtosBytecodeRootNode root,
            BytecodeNode bytecode,
            int bytecodeIndex,
            ProtosFunctionCallDispatchers.CallNode call) {
        Object outcome = call.execute(
                frame, terminal, method,
                ProtosFrameArguments.dispatchedCall1(
                        null, ProtosFrameArguments.functionCallMarker(),
                        null, null, null,
                        ProtosNumericValueSupport.guestValue(argument)),
                null, root);
        return ProtosTailCallContinuation.completeCall(
                terminal, outcome, bytecode, bytecodeIndex);
    }

    static TruffleString toTruffleString(String selector) {
        return TruffleString.fromJavaStringUncached(selector, TruffleString.Encoding.UTF_16);
    }
}
