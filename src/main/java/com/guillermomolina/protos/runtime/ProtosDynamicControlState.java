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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Task-local dynamic control state used by replay-stable standard control primitives.
 *
 * <p>This is internal runtime machinery, not a Protos value. It is deliberately owned by one
 * {@link ProtosTask}: synchronous activations attached to that task share the state, while a
 * distinct child task starts with independent dynamic control state.
 *
 * <p>Frames are keyed by a replay-stable invocation identity. Current evaluator invocation
 * activations already provide such an identity across suspension/replay, allowing later I022
 * slices to re-enter an installation site without duplicating a semantic handler/ensure frame.
 */
public final class ProtosDynamicControlState {
    public enum FrameKind {
        HANDLER,
        ENSURE,
        WHILE
    }

    public enum EnsurePhase {
        BODY,
        CLEANUP
    }

    public enum WhilePhase {
        CONDITION,
        BODY
    }

    public enum EnsureExitKind {
        NORMAL,
        ERROR,
        RETURN,
        CANCELLATION
    }

    public enum TransferKind {
        RETURN,
        ERROR,
        CANCELLATION
    }

    /** Opaque internal identity for one replay-stable dynamic control installation. */
    public static final class Frame {
        private final long id;
        private final FrameKind kind;
        private final Object invocationIdentity;
        private boolean active = true;
        private ProtosObjectValue handlerMatchPrototype;
        private EnsurePhase ensurePhase;
        private EnsureExitKind ensureExitKind;
        private Object ensureOutcome;
        private int ensureBodyReplayCursor = -1;
        private WhilePhase whilePhase;
        private boolean whileCallbackCheckpointBound;
        private int whileCallbackCheckpoint = -1;

        private Frame(long id, FrameKind kind, Object invocationIdentity) {
            this.id = id;
            this.kind = Objects.requireNonNull(kind, "kind");
            this.invocationIdentity = Objects.requireNonNull(invocationIdentity, "invocationIdentity");
            this.ensurePhase = kind == FrameKind.ENSURE ? EnsurePhase.BODY : null;
            this.whilePhase = kind == FrameKind.WHILE ? WhilePhase.CONDITION : null;
        }

        public long id() {
            return id;
        }

        public FrameKind kind() {
            return kind;
        }

        public Object invocationIdentity() {
            return invocationIdentity;
        }

        public boolean active() {
            return active;
        }

        public Optional<ProtosObjectValue> handlerMatchPrototype() {
            return Optional.ofNullable(handlerMatchPrototype);
        }

        public EnsurePhase ensurePhase() {
            requireEnsureFrame();
            return ensurePhase;
        }

        public Optional<EnsureExitKind> ensureExitKind() {
            requireEnsureFrame();
            return Optional.ofNullable(ensureExitKind);
        }

        public Optional<Object> ensureOutcome() {
            requireEnsureFrame();
            return Optional.ofNullable(ensureOutcome);
        }

        public int ensureBodyReplayCursor() {
            requireEnsureFrame();
            if (ensurePhase != EnsurePhase.CLEANUP) {
                throw new IllegalStateException("ensure body has not exited yet");
            }
            return ensureBodyReplayCursor;
        }

        public WhilePhase whilePhase() {
            requireWhileFrame();
            return whilePhase;
        }

        private int bindWhileCallbackCheckpoint(int replayCursor) {
            requireWhileFrame();
            if (replayCursor < -1) {
                throw new IllegalArgumentException("invalid while callback replay cursor");
            }
            if (!whileCallbackCheckpointBound) {
                whileCallbackCheckpoint = replayCursor;
                whileCallbackCheckpointBound = true;
            } else if (whileCallbackCheckpoint != replayCursor) {
                throw new IllegalStateException(
                        "while callback replay checkpoint changed from "
                                + whileCallbackCheckpoint
                                + " to "
                                + replayCursor);
            }
            return whileCallbackCheckpoint;
        }

        private void bindHandlerMatchPrototype(ProtosObjectValue matchPrototype) {
            Objects.requireNonNull(matchPrototype, "matchPrototype");
            if (handlerMatchPrototype != null && handlerMatchPrototype != matchPrototype) {
                throw new IllegalStateException(
                        "replay invocation changed handler match prototype");
            }
            handlerMatchPrototype = matchPrototype;
        }

        private void beginEnsureCleanup(
                EnsureExitKind exitKind, Object outcome, int bodyReplayCursor) {
            requireEnsureFrame();
            Objects.requireNonNull(exitKind, "exitKind");
            Objects.requireNonNull(outcome, "outcome");
            if (ensurePhase != EnsurePhase.BODY) {
                throw new IllegalStateException("ensure body exit was already recorded");
            }
            if (bodyReplayCursor < -1) {
                throw new IllegalArgumentException("invalid ensure body replay cursor");
            }
            ensureExitKind = exitKind;
            ensureOutcome = outcome;
            ensureBodyReplayCursor = bodyReplayCursor;
            ensurePhase = EnsurePhase.CLEANUP;
        }

        private void completeWhileInvocation(
                WhilePhase expectedPhase, WhilePhase nextPhase) {
            requireWhileFrame();
            if (whilePhase != expectedPhase) {
                throw new IllegalStateException(
                        "while invocation completed in unexpected phase " + whilePhase);
            }
            whilePhase = nextPhase;
        }

        private void requireEnsureFrame() {
            if (kind != FrameKind.ENSURE) {
                throw new IllegalStateException("non-ensure frame has no ensure phase");
            }
        }

        private void requireWhileFrame() {
            if (kind != FrameKind.WHILE) {
                throw new IllegalStateException("non-while frame has no while phase");
            }
        }

        private void deactivate() {
            active = false;
        }
    }

    /** Opaque internal record for the one transfer currently being unwound by a task. */
    public static final class Transfer {
        private final long id;
        private final TransferKind kind;
        private final Object payload;

        private Transfer(long id, TransferKind kind, Object payload) {
            this.id = id;
            this.kind = Objects.requireNonNull(kind, "kind");
            this.payload = payload;
        }

        public long id() {
            return id;
        }

        public TransferKind kind() {
            return kind;
        }

        public Optional<Object> payload() {
            return Optional.ofNullable(payload);
        }
    }

    private final IdentityHashMap<Object, Frame> framesByInvocation = new IdentityHashMap<>();
    private final ArrayDeque<Frame> framesNewestFirst = new ArrayDeque<>();
    private long nextFrameId = 1L;
    private long nextTransferId = 1L;
    private Transfer activeTransfer;

    public Frame enterFrame(Object invocationIdentity, FrameKind kind) {
        Objects.requireNonNull(invocationIdentity, "invocationIdentity");
        Objects.requireNonNull(kind, "kind");

        Frame existing = framesByInvocation.get(invocationIdentity);
        if (existing != null) {
            if (existing.kind() != kind) {
                throw new IllegalStateException(
                        "replay invocation changed dynamic frame kind from "
                                + existing.kind()
                                + " to "
                                + kind);
            }
            return existing;
        }

        Frame created = new Frame(nextFrameId++, kind, invocationIdentity);
        framesByInvocation.put(invocationIdentity, created);
        framesNewestFirst.addFirst(created);
        return created;
    }

    public Frame enterHandlerFrame(
            Object invocationIdentity, ProtosObjectValue matchPrototype) {
        Frame frame = enterFrame(invocationIdentity, FrameKind.HANDLER);
        frame.bindHandlerMatchPrototype(matchPrototype);
        return frame;
    }

    public Optional<Frame> selectMatchingHandler(ProtosObjectValue error) {
        Objects.requireNonNull(error, "error");
        for (Frame frame : framesNewestFirst) {
            if (frame.active()
                    && frame.kind() == FrameKind.HANDLER
                    && frame.handlerMatchPrototype().isPresent()
                    && matches(error, frame.handlerMatchPrototype().orElseThrow())) {
                frame.deactivate();
                return Optional.of(frame);
            }
        }
        return Optional.empty();
    }

    private static boolean matches(
            ProtosObjectValue error, ProtosObjectValue matchPrototype) {
        ProtosObjectValue current = error;
        while (true) {
            if (current == matchPrototype) {
                return true;
            }
            Object parent = current.parent().orElse(null);
            if (!(parent instanceof ProtosObjectValue parentObject)) {
                return false;
            }
            current = parentObject;
        }
    }

    public void beginEnsureCleanup(
            Frame frame,
            EnsureExitKind exitKind,
            Object outcome,
            int bodyReplayCursor) {
        requirePresent(frame);
        frame.beginEnsureCleanup(exitKind, outcome, bodyReplayCursor);
    }

    public int bindWhileCallbackCheckpoint(Frame frame, int replayCursor) {
        requirePresent(frame);
        return frame.bindWhileCallbackCheckpoint(replayCursor);
    }

    public void completeWhileCondition(Frame frame) {
        requirePresent(frame);
        frame.completeWhileInvocation(WhilePhase.CONDITION, WhilePhase.BODY);
    }

    public void completeWhileBody(Frame frame) {
        requirePresent(frame);
        frame.completeWhileInvocation(WhilePhase.BODY, WhilePhase.CONDITION);
    }

    public boolean hasActiveEnsureFrames() {
        for (Frame frame : framesNewestFirst) {
            if (frame.active() && frame.kind() == FrameKind.ENSURE) {
                return true;
            }
        }
        return false;
    }

    public Optional<Frame> frameForInvocation(Object invocationIdentity) {
        Objects.requireNonNull(invocationIdentity, "invocationIdentity");
        return Optional.ofNullable(framesByInvocation.get(invocationIdentity));
    }

    public List<Frame> framesNewestFirst() {
        return List.copyOf(new ArrayList<>(framesNewestFirst));
    }

    public void deactivate(Frame frame) {
        requirePresent(frame);
        frame.deactivate();
    }

    public void leaveFrame(Frame frame) {
        requirePresent(frame);
        Frame newest = framesNewestFirst.peekFirst();
        if (newest != frame) {
            throw new IllegalStateException("dynamic control frames must leave in LIFO order");
        }
        frame.deactivate();
        framesNewestFirst.removeFirst();
        framesByInvocation.remove(frame.invocationIdentity());
    }

    public Transfer beginTransfer(TransferKind kind, Object payload) {
        if (activeTransfer != null) {
            throw new IllegalStateException("a dynamic control transfer is already active");
        }
        Transfer transfer = new Transfer(nextTransferId++, kind, payload);
        activeTransfer = transfer;
        return transfer;
    }

    public Transfer replaceTransfer(TransferKind kind, Object payload) {
        if (activeTransfer == null) {
            throw new IllegalStateException("no dynamic control transfer is active");
        }
        Transfer transfer = new Transfer(nextTransferId++, kind, payload);
        activeTransfer = transfer;
        return transfer;
    }

    public Optional<Transfer> activeTransfer() {
        return Optional.ofNullable(activeTransfer);
    }

    public boolean clearTransfer(Transfer transfer) {
        Objects.requireNonNull(transfer, "transfer");
        if (activeTransfer != transfer) {
            return false;
        }
        activeTransfer = null;
        return true;
    }

    private void requirePresent(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        Frame present = framesByInvocation.get(frame.invocationIdentity());
        if (present != frame) {
            throw new IllegalArgumentException("dynamic control frame is not present in this task state");
        }
    }
}
