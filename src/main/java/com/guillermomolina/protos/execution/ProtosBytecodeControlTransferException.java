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

import com.guillermomolina.protos.runtime.ProtosNonLocalReturnException;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.ControlFlowException;
import java.util.Objects;

/**
 * Backend-private PLAT021 bridge that lets selected internal control transfers
 * participate in Bytecode DSL guest exception handling without changing their
 * semantic identity or making the bridge itself authoritative.
 */
// Serializable only by exception hierarchy; runtime state is intentionally not Java-serializable.
@SuppressWarnings("serial")
final class ProtosBytecodeControlTransferException extends AbstractTruffleException {
    private static final long serialVersionUID = 1L;

    private final ControlFlowException transfer;
    /*
     * CLI008-C1: the semantic bytecode position where the transfer was bridged, so a Task failure
     * fabricated from it (D177 InvalidReturn) can project its innermost frame. Stored at
     * construction only: a bridge stays an ordinary allocation on non-local-return paths.
     */
    private final BytecodeNode originBytecode;
    private final int originBytecodeIndex;

    private ProtosBytecodeControlTransferException(
            ControlFlowException transfer,
            BytecodeNode originBytecode,
            int originBytecodeIndex) {
        super();
        this.transfer = Objects.requireNonNull(transfer, "transfer");
        this.originBytecode = originBytecode;
        this.originBytecodeIndex = originBytecodeIndex;
    }

    static ProtosBytecodeControlTransferException bridge(ControlFlowException transfer) {
        return bridge(transfer, null, -1);
    }

    static ProtosBytecodeControlTransferException bridge(
            ControlFlowException transfer,
            BytecodeNode originBytecode,
            int originBytecodeIndex) {
        Objects.requireNonNull(transfer, "transfer");
        if (!(transfer instanceof ProtosNonLocalReturnException)
                && !(transfer instanceof ProtosTaskCancellationException)) {
            throw transfer;
        }
        return new ProtosBytecodeControlTransferException(
                transfer, originBytecode, originBytecodeIndex);
    }

    ControlFlowException transfer() {
        return transfer;
    }

    BytecodeNode originBytecode() {
        return originBytecode;
    }

    int originBytecodeIndex() {
        return originBytecodeIndex;
    }
}
