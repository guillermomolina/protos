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
 * the specific language governing rights and limitations under the LICENSE.
 */

package com.guillermomolina.protos.execution;

import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * PLAT015 NodeLibrary bridge for Bytecode DSL instrumentation locations.
 *
 * <p>The Bytecode interpreter's own locals are implementation state, not Protos
 * lexical bindings. Tooling therefore projects the exact activation from frame
 * argument zero through the same bounded {@link ProtosDebuggerScope} used by the
 * AST backend.</p>
 */
@ExportLibrary(value = NodeLibrary.class, receiverType = TagTreeNode.class)
final class ProtosBytecodeTagTreeNodeExports {
    private ProtosBytecodeTagTreeNodeExports() {}

    @ExportMessage
    static boolean hasScope(
            @SuppressWarnings("unused") TagTreeNode node,
            Frame frame) {
        return ProtosFrameArguments.hasActivation(frame);
    }

    @ExportMessage
    static Object getScope(
            @SuppressWarnings("unused") TagTreeNode node,
            Frame frame,
            @SuppressWarnings("unused") boolean nodeEnter)
            throws UnsupportedMessageException {
        if (!hasScope(node, frame)) {
            throw UnsupportedMessageException.create();
        }
        return new ProtosDebuggerScope(ProtosFrameArguments.activation(frame));
    }
}
