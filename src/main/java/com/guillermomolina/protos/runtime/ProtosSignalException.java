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

package com.guillermomolina.protos.runtime;

import com.guillermomolina.protos.runtime.diagnostics.ProtosDiagnosticTrace;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import java.util.Objects;
import java.util.Optional;

/**
 * One Error transfer occurrence.
 *
 * <p>CLI008-C1: the occurrence, not the Error value, owns diagnostic provenance. The exception
 * carries no location node, so the first semantic Bytecode root it crosses records its exact
 * bytecode position here (failure path only); a terminal boundary projects it, together with the
 * Truffle guest stack, into an inert {@link ProtosDiagnosticTrace} before discarding the exception.
 */
// Serializable only by exception hierarchy; runtime state is intentionally not Java-serializable.
@SuppressWarnings("serial")
public final class ProtosSignalException extends AbstractTruffleException {
    private final ProtosObjectValue error;
    private ProtosDynamicControlState.Frame selectedHandlerFrame;
    private BytecodeNode originBytecode;
    private int originBytecodeIndex = -1;
    private ProtosDiagnosticTrace terminalDiagnosticTrace;
    /*
     * Uncached-interpreter frames keep their bytecode index only in a frame slot that later
     * cleanup in the same frame may overwrite before the stack trace is read, so the exact
     * position of each such frame is recorded at its first crossing. Interpreter-only, failure
     * path only; keyed by the physical frame identity.
     */
    private java.util.IdentityHashMap<Object, BytecodeLocation> interpreterPositions;
    /*
     * I112 / PLAT062-T: logical caller positions of terminal invocations whose caller root had
     * already returned its suspension transport when this occurrence crossed the resumption,
     * innermost first, each with the number of physical frames live at that crossing. Failure
     * path only; never a physical frame.
     */
    private java.util.List<BytecodeLocation> resumedTerminalCallers;
    private java.util.List<Integer> resumedTerminalCallerLiveFrames;

    public ProtosSignalException(ProtosObjectValue error) {
        super();
        this.error = Objects.requireNonNull(error, "error");
    }

    public ProtosObjectValue error() {
        return error;
    }

    /**
     * Records the bytecode position where this occurrence first crossed a semantic Bytecode root.
     * Later crossings (outer roots, or a rethrow of this same transfer after ensure cleanup) keep
     * the first, innermost position.
     */
    public void recordOriginIfAbsentForRuntime(BytecodeNode bytecode, int bytecodeIndex) {
        if (originBytecode == null && bytecode != null && bytecodeIndex >= 0) {
            originBytecode = bytecode;
            originBytecodeIndex = bytecodeIndex;
        }
    }

    /**
     * Records the exact position of an uncached-interpreter frame the first time this occurrence
     * crosses it.
     */
    @TruffleBoundary
    public void recordInterpreterPositionIfAbsentForRuntime(
            Object frame, BytecodeNode bytecode, int bytecodeIndex) {
        if (frame == null || bytecode == null || bytecodeIndex < 0) {
            return;
        }
        if (interpreterPositions == null) {
            interpreterPositions = new java.util.IdentityHashMap<>();
        }
        interpreterPositions.computeIfAbsent(
                frame, ignored -> bytecode.getBytecodeLocation(bytecodeIndex));
    }

    /**
     * I112: records the logical position of a terminal caller that is no longer a physical frame
     * when this occurrence crosses its resumed transport, with the number of physical frames live
     * at that crossing (its insertion point among the unwound frames).
     */
    @TruffleBoundary
    public void recordResumedTerminalCallerForRuntime(
            BytecodeLocation caller, int livePhysicalFrames) {
        if (caller == null) {
            return;
        }
        if (resumedTerminalCallers == null) {
            resumedTerminalCallers = new java.util.ArrayList<>(2);
            resumedTerminalCallerLiveFrames = new java.util.ArrayList<>(2);
        }
        resumedTerminalCallers.add(caller);
        resumedTerminalCallerLiveFrames.add(livePhysicalFrames);
    }

    /** Transient capture input; innermost first. */
    public int resumedTerminalCallerCountForRuntime() {
        return resumedTerminalCallers == null ? 0 : resumedTerminalCallers.size();
    }

    public BytecodeLocation resumedTerminalCallerForRuntime(int index) {
        return resumedTerminalCallers.get(index);
    }

    public int resumedTerminalCallerLiveFramesForRuntime(int index) {
        return resumedTerminalCallerLiveFrames.get(index);
    }

    /** Transient capture input: the recorded position of {@code frame}, or {@code null}. */
    public BytecodeLocation interpreterPositionForRuntime(Object frame) {
        return interpreterPositions == null || frame == null
                ? null
                : interpreterPositions.get(frame);
    }

    /** Transient capture input; never retained beyond this exception. */
    public BytecodeNode originBytecodeForRuntime() {
        return originBytecode;
    }

    public int originBytecodeIndexForRuntime() {
        return originBytecodeIndex;
    }

    /**
     * Attaches the inert trace captured when this occurrence escaped a presenting boundary that has
     * no Task (the persistent REPL unit). Write-once: a later attachment is ignored.
     */
    public void attachTerminalDiagnosticTraceForRuntime(ProtosDiagnosticTrace trace) {
        if (terminalDiagnosticTrace == null) {
            terminalDiagnosticTrace = trace;
        }
    }

    public Optional<ProtosDiagnosticTrace> terminalDiagnosticTrace() {
        return Optional.ofNullable(terminalDiagnosticTrace);
    }

    public Optional<ProtosDynamicControlState.Frame> selectedHandlerFrame() {
        return Optional.ofNullable(selectedHandlerFrame);
    }

    void selectHandlerFrame(ProtosDynamicControlState.Frame frame) {
        Objects.requireNonNull(frame, "frame");
        if (selectedHandlerFrame != null && selectedHandlerFrame != frame) {
            throw new IllegalStateException("Error transfer already selected another handler");
        }
        selectedHandlerFrame = frame;
    }

    /**
     * D189: withdraws the handler selected while this Error left a synchronous foreign callback.
     * Such a frame is necessarily outside the callback, and the Error now belongs to the foreign
     * operation; if that operation returns it unchanged, it is selected again on re-propagation,
     * and if the operation replaces it, the handler remains available to the replacement.
     */
    public void releaseHandlerSelectionForForeignCallbackForRuntime() {
        if (selectedHandlerFrame != null) {
            selectedHandlerFrame.releaseSelectionForRuntime();
            selectedHandlerFrame = null;
        }
    }
}
