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

import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * I115-H7 EXPERIMENTAL, proxied as the {@code NumberInPlaceAdd} operation
 * (GraalPy {@code PyNumberInPlaceAddNode}): the compact lowering of {@code
 * x = x + e} when the stored binding is the same binding as the left operand.
 * Protos has no {@code +=} syntax; this is a physical form of the same
 * ordinary {@code +}. The primitive fast paths are shared with {@code
 * NumberAdd}; this node owns its separate generic fallback.
 *
 * <p>RECORDED DIVERGENCE: Protos has no in-place addition protocol, so the
 * fallback is the ordinary {@code +} send, exactly like {@code NumberAdd}.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
abstract class ProtosExperimentalNumberInPlaceAddNode
        extends ProtosExperimentalNumberAddFastPathsBase {

    @Specialization(replaces = "doLL")
    static Object doLLOvf(
            VirtualFrame frame,
            long left,
            long right,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return addOrPromote(root, frame, left, right);
    }

    @Fallback
    @InliningCutoff
    static Object doGeneric(
            VirtualFrame frame,
            Object left,
            Object right,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return genericAdd(root, frame, left, right);
    }
}
