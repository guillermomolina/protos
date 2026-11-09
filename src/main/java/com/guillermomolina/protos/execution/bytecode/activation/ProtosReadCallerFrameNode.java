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
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * I115: GraalPy ReadFrameNode-style cold caller recovery.
 *
 * The ordinary dispatcher leaves the caller slot empty until the
 * effective callee requests it. On first demand, a Truffle stack walk
 * recovers the active caller and registers the callee's caller need.
 * Subsequent calls use the existing demand-driven B5 transport.
 *
 * FrameInstance observations and the temporary Frame remain confined to
 * this cold recovery operation while the caller is on the active stack.
 * Only the returned exact ProtosActivation may be retained by the invocation;
 * no frame or wrapper escapes into a B5 caller slot. Escaping captures resolve
 * their owner before returning; this fallback cannot recover a completed frame.
 * The ordinary direct-call
 * path gains no persistent frame, wrapper, or allocation.
 *
 * <p>The active stack is not a source of caller identity after continuation
 * resumption: the original caller frame has returned, and whatever frames
 * now surround the resumed continuation are its resumer, not its caller. A
 * suspended invocation's exact caller is sealed into its B5[3] at the
 * original suspension boundary
 * ({@link ProtosFrameArguments#sealSuspendedCaller}). When this walk finds
 * the invocation executing under a non-semantic (continuation) root, it
 * fails instead of reading any outer frame. There is no synthetic-frame,
 * module, registry or thread-local fallback.
 *
 * <p>A new callee invoked from inside a resumed source caller is different:
 * its immediate physical caller is a {@link ContinuationRootNode} whose
 * source root is the semantic caller. The continuation root's own arguments
 * belong to the continuation calling convention; the caller is the saved
 * source interpreter frame carried among them, admitted only when its
 * descriptor matches the semantic source root. A continuation root that
 * cannot supply such a frame fails closed and never lends its resumer.
 */
final class ProtosReadCallerFrameNode {

    private record LocatedCaller(
            Frame frame,
            ProtosBytecodeRootNode calleeRoot) {}

    /** Walk outcome for a callee found under a resumed continuation root. */
    private static final LocatedCaller RESUMED_CALLEE = new LocatedCaller(null, null);

    /** Walk outcome for a continuation caller without a valid saved source frame. */
    private static final LocatedCaller INVALID_CONTINUATION_CALLER =
            new LocatedCaller(null, null);

    private ProtosReadCallerFrameNode() {}

    @TruffleBoundary
    static ProtosActivation resolve(Object[] calleeArguments) {
        return resolve(calleeArguments, true);
    }

    /**
     * @param registerCallerDemand whether the callee records
     *     NEEDS_EXACT_ACTIVATION; the suspension seal materializes a live
     *     caller without registering demand merely because a suspension
     *     occurred
     */
    @TruffleBoundary
    static ProtosActivation resolve(
            Object[] calleeArguments, boolean registerCallerDemand) {
        LocatedCaller located = Truffle.getRuntime().iterateFrames(
                new FrameInstanceVisitor<LocatedCaller>() {
                    private boolean foundCallee;
                    private ProtosBytecodeRootNode calleeRoot;

                    @Override
                    public LocatedCaller visitFrame(
                            FrameInstance instance) {
                        Frame frame = instance.getFrame(
                                FrameInstance.FrameAccess.READ_ONLY);
                        RootNode root = ((RootCallTarget)
                                instance.getCallTarget()).getRootNode();

                        if (!foundCallee) {
                            if (frame.getArguments() == calleeArguments) {
                                foundCallee = true;
                                if (root instanceof ProtosBytecodeRootNode
                                        semantic
                                        && semantic.isSemanticActivationRoot()) {
                                    calleeRoot = semantic;
                                } else {
                                    // Resumed continuation: outer frames are
                                    // its resumer, never its caller.
                                    return RESUMED_CALLEE;
                                }
                            }
                            return null;
                        }

                        if (calleeRoot == null) {
                            return null;
                        }

                        if (root instanceof ContinuationRootNode continuation) {
                            Frame saved = savedSourceFrame(frame, continuation);
                            return saved != null
                                    ? new LocatedCaller(saved, calleeRoot)
                                    : INVALID_CONTINUATION_CALLER;
                        }

                        boolean sourceRoot =
                                root instanceof ProtosBytecodeRootNode semantic
                                        && semantic.isSemanticActivationRoot();
                        boolean builtinRoot =
                                root instanceof ProtosBuiltinCallRootNode;

                        if ((sourceRoot || builtinRoot)
                                && ProtosFrameArguments.hasActivation(frame)) {
                            return new LocatedCaller(frame, calleeRoot);
                        }

                        return null;
                    }
                });

        if (located == RESUMED_CALLEE) {
            throw new IllegalStateException(
                    "I115: resumed invocation has no sealed caller;"
                            + " stack recovery cannot identify a returned caller frame");
        }
        if (located == INVALID_CONTINUATION_CALLER) {
            throw new IllegalStateException(
                    "I115: continuation caller has no saved semantic source frame;"
                            + " its resumer cannot stand in for the caller");
        }
        if (located == null) {
            throw new IllegalStateException(
                    "I115: caller frame unavailable for stack recovery");
        }

        ProtosActivation caller =
                ProtosFrameArguments.activation(located.frame());

        /*
         * GraalPy PRootNode.updateCallerFlags / ReadFrameNode:
         * register the missing caller dependency and invalidate
         * incoming direct-call assumptions for subsequent calls.
         */
        if (registerCallerDemand) {
            located.calleeRoot().requestCallerNeed(
                    ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION);
        }

        return caller;
    }

    /**
     * Same normalization as ProtosDiagnosticTraceCapture: a continuation
     * root's semantic identity is its source root, and its saved interpreter
     * frame is the source invocation's frame. Returns null unless both are
     * genuine semantic activation state.
     */
    private static Frame savedSourceFrame(
            Frame continuationFrame, ContinuationRootNode continuation) {
        if (!(continuation.getSourceRootNode() instanceof ProtosBytecodeRootNode semantic)
                || !semantic.isSemanticActivationRoot()) {
            return null;
        }
        for (Object argument : continuationFrame.getArguments()) {
            if (argument instanceof Frame candidate
                    && candidate.getFrameDescriptor() == semantic.getFrameDescriptor()) {
                return ProtosFrameArguments.hasActivation(candidate) ? candidate : null;
            }
        }
        return null;
    }
}
