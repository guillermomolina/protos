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

import com.oracle.truffle.api.bytecode.BytecodeLocal;

/**
 * PLAT070 accounting of internal control transfers crossing an I/O C-prime root.
 *
 * <p>Non-local returns and Task cancellation unwind directly through generated frames and never
 * enter a guest {@code TryCatch}, so an I/O C-prime root can no longer observe them as a failed
 * target invocation. Each single-invocation operation root (BufferedReader, BufferedWriter,
 * TextReader, TextWriter) therefore runs its whole body inside a Bytecode {@code TryFinally}
 * whose handler applies the protocol's own abrupt-transfer bookkeeping ({@code
 * IoAccountEscapingTransfer}) exactly when the body neither completed nor left through a guest
 * Error:
 *
 * <pre>
 * completed = false
 * try:
 *     try:
 *         body ... ; completed = true ; return result
 *     catch guest Error e:
 *         completed = true ; rethrow e          (unchanged guest Error treatment)
 * finally:
 *     IoAccountEscapingTransfer(state, completed)
 * </pre>
 *
 * <p>The transfer itself is never caught, wrapped or replaced: it keeps propagating as the same
 * object after the handler. The marker is a local of the root's own frame, so a body that yields
 * keeps it in its continuation and no completed work is replayed on resumption. The entry state
 * is always argument 1 of these roots.
 *
 * <p>The lifecycle-release root uses the same shape but its finally handler additionally runs the
 * pending mandatory finalizers ({@code ProtosIoReleaseCPrimeExecution#emitTransferCleanup}).
 */
final class ProtosIoCPrimeTransferAccounting {
    private ProtosIoCPrimeTransferAccounting() {}

    /** Opens the accounted extent; returns its completion marker. */
    static BytecodeLocal begin(ProtosBytecodeRootNodeGen.Builder builder) {
        BytecodeLocal completed = builder.createLocal("ioCPrimeCompletedOrSignalled", null);
        builder.beginStoreLocal(completed);
        builder.emitLoadConstant(false);
        builder.endStoreLocal();

        builder.beginTryFinally(
                () -> {
                    builder.beginIoAccountEscapingTransfer();
                    builder.emitLoadArgument(1);
                    builder.emitLoadLocal(completed);
                    builder.endIoAccountEscapingTransfer();
                });
        builder.beginTryCatch();
        builder.beginBlock();
        return completed;
    }

    /** Marks normal completion; emitted immediately before the root's final return. */
    static void markCompleted(ProtosBytecodeRootNodeGen.Builder builder, BytecodeLocal completed) {
        builder.beginStoreLocal(completed);
        builder.emitLoadConstant(true);
        builder.endStoreLocal();
    }

    /** Closes the accounted extent opened by {@link #begin}. */
    static void end(ProtosBytecodeRootNodeGen.Builder builder, BytecodeLocal completed) {
        builder.endBlock();

        builder.beginBlock();
        markCompleted(builder, completed);
        builder.beginRethrowTruffleException();
        builder.emitLoadException();
        builder.endRethrowTruffleException();
        builder.endBlock();

        builder.endTryCatch();
        builder.endTryFinally();
    }
}
