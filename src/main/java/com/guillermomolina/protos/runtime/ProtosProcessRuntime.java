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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Internal local Protos Process failure-domain and RootActor failure-authority substrate.
 *
 * <p>A Process is semantic runtime capacity, not an operating-system process. This class performs
 * no OS termination and exposes no language-level Process capability. It only owns the current
 * local Process incarnation, its unique RootActor, and the Actor incarnations currently hosted by
 * that Process so the already-closed Process/Actor failure consequences have one authoritative
 * implementation boundary.
 */
public final class ProtosProcessRuntime {
    public enum LifecycleState {
        RUNNING,
        TERMINATING,
        TERMINATED
    }

    public enum ArgumentsSnapshotState {
        UNESTABLISHED,
        AVAILABLE,
        UNREPRESENTABLE
    }

    public enum EnvironmentSnapshotState {
        UNESTABLISHED,
        AVAILABLE,
        INVALID
    }

    /**
     * Stable bootstrap state of one standard-stream Encoding association.
     *
     * <p>INVALID is distinct from UNAVAILABLE: it records a host configuration whose stream
     * availability and Encoding association disagree.
     */
    public enum StandardStreamEncodingState {
        UNESTABLISHED,
        AVAILABLE,
        UNAVAILABLE,
        INVALID
    }

    private final ProtosActor rootActor;
    private final Set<ProtosActor> liveActors = new LinkedHashSet<>();
    private LifecycleState lifecycle = LifecycleState.RUNNING;
    private Object rootFailureCause;
    private ArgumentsSnapshotState argumentsSnapshotState =
            ArgumentsSnapshotState.UNESTABLISHED;
    private ProtosProcessArgumentsValue argumentsSnapshot;
    private EnvironmentSnapshotState environmentSnapshotState =
            EnvironmentSnapshotState.UNESTABLISHED;
    private ProtosEnvironmentValue environmentSnapshot;
    private boolean standardStreamsEstablished;
    private ProtosProcessStandardStreamBinding stdinBinding;
    private ProtosProcessStandardStreamBinding stdoutBinding;
    private ProtosProcessStandardStreamBinding stderrBinding;
    private StandardStreamEncodingState stdinEncodingState =
            StandardStreamEncodingState.UNESTABLISHED;
    private StandardStreamEncodingState stdoutEncodingState =
            StandardStreamEncodingState.UNESTABLISHED;
    private StandardStreamEncodingState stderrEncodingState =
            StandardStreamEncodingState.UNESTABLISHED;
    private ProtosEncodingValue stdinEncoding;
    private ProtosEncodingValue stdoutEncoding;
    private ProtosEncodingValue stderrEncoding;

    /** Creates one Process incarnation together with its unique RootActor. */
    public ProtosProcessRuntime(ProtosObjectValue actorRefPrototype) {
        rootActor =
                new ProtosActor(
                        Objects.requireNonNull(actorRefPrototype, "actorRefPrototype"),
                        this,
                        true);
        liveActors.add(rootActor);
    }

    public synchronized LifecycleState lifecycleState() {
        return lifecycle;
    }

    public ProtosActor rootActorForRuntime() {
        return rootActor;
    }

    /**
     * Captures the complete application-argument bootstrap snapshot exactly once.
     *
     * <p>The host supplies application arguments only; executable/argv[0] identity is outside this
     * sequence. The complete detached host list is validated before any portable snapshot becomes
     * available. An unrepresentable element records one stable UNREPRESENTABLE bootstrap outcome;
     * later host mutation or a second establishment attempt cannot change that result.
     */
    public synchronized ArgumentsSnapshotState establishArgumentsForRuntime(
            ProtosObjectValue argumentsPrototype, List<String> hostArguments) {
        Objects.requireNonNull(argumentsPrototype, "argumentsPrototype");
        Objects.requireNonNull(hostArguments, "hostArguments");
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process arguments cannot be established after termination begins");
        }
        if (argumentsSnapshotState != ArgumentsSnapshotState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process arguments bootstrap snapshot is already established");
        }

        try {
            argumentsSnapshot =
                    ProtosProcessArgumentsValue.captureForRuntime(
                            argumentsPrototype, hostArguments);
            argumentsSnapshotState = ArgumentsSnapshotState.AVAILABLE;
        } catch (IllegalArgumentException | NullPointerException unrepresentable) {
            argumentsSnapshot = null;
            argumentsSnapshotState = ArgumentsSnapshotState.UNREPRESENTABLE;
        }
        return argumentsSnapshotState;
    }

    public synchronized ArgumentsSnapshotState argumentsSnapshotStateForRuntime() {
        return argumentsSnapshotState;
    }

    /**
     * Returns the canonical Process argument snapshot when bootstrap conversion succeeded.
     *
     * <p>UNESTABLISHED is a launcher/integration error. UNREPRESENTABLE intentionally returns empty
     * so the later Process accessor can create a fresh ordinary Error occurrence per failed call.
     */
    public synchronized Optional<ProtosProcessArgumentsValue>
            argumentsSnapshotForRuntime() {
        if (argumentsSnapshotState == ArgumentsSnapshotState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process arguments bootstrap snapshot is not established");
        }
        return Optional.ofNullable(argumentsSnapshot);
    }

    public synchronized EnvironmentSnapshotState establishEnvironmentForRuntime(
            ProtosObjectValue environmentPrototype,
            ProtosEnvironmentValue.NativeNameDomain nameDomain,
            List<ProtosEnvironmentValue.NativeEntry> hostEntries) {
        Objects.requireNonNull(environmentPrototype, "environmentPrototype");
        Objects.requireNonNull(nameDomain, "nameDomain");
        Objects.requireNonNull(hostEntries, "hostEntries");
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process environment cannot be established after termination begins");
        }
        if (environmentSnapshotState != EnvironmentSnapshotState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process Environment bootstrap snapshot is already established");
        }

        try {
            environmentSnapshot =
                    ProtosEnvironmentValue.captureForRuntime(
                            environmentPrototype, nameDomain, hostEntries);
            environmentSnapshotState = EnvironmentSnapshotState.AVAILABLE;
        } catch (IllegalArgumentException | NullPointerException invalidMapping) {
            environmentSnapshot = null;
            environmentSnapshotState = EnvironmentSnapshotState.INVALID;
        }
        return environmentSnapshotState;
    }

    public synchronized EnvironmentSnapshotState environmentSnapshotStateForRuntime() {
        return environmentSnapshotState;
    }

    public synchronized Optional<ProtosEnvironmentValue> environmentSnapshotForRuntime() {
        if (environmentSnapshotState == EnvironmentSnapshotState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process Environment bootstrap snapshot is not established");
        }
        return Optional.ofNullable(environmentSnapshot);
    }

    /**
     * Establishes the three independently optional standard byte-stream bindings exactly once.
     *
     * <p>Null backend means that binding is unavailable. Every available binding is already usable
     * synchronously after this call; this method performs no deferred/waiting acquisition.
     */
    public synchronized void establishStandardStreamsForRuntime(
            ProtosObjectValue readablePrototype,
            ProtosObjectValue writablePrototype,
            ProtosObjectValue bytesPrototype,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend) {
        Objects.requireNonNull(readablePrototype, "readablePrototype");
        Objects.requireNonNull(writablePrototype, "writablePrototype");
        Objects.requireNonNull(bytesPrototype, "bytesPrototype");
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process standard streams cannot be established after termination begins");
        }
        if (standardStreamsEstablished) {
            throw new IllegalStateException(
                    "Process standard streams are already established");
        }

        stdinBinding =
                stdinBackend == null
                        ? null
                        : ProtosProcessStandardStreamBinding.readableForRuntime(
                                this,
                                readablePrototype,
                                bytesPrototype,
                                stdinBackend);
        stdoutBinding =
                stdoutBackend == null
                        ? null
                        : ProtosProcessStandardStreamBinding.writableForRuntime(
                                this,
                                writablePrototype,
                                bytesPrototype,
                                stdoutBackend);
        stderrBinding =
                stderrBackend == null
                        ? null
                        : ProtosProcessStandardStreamBinding.writableForRuntime(
                                this,
                                writablePrototype,
                                bytesPrototype,
                                stderrBackend);
        standardStreamsEstablished = true;
    }

    public synchronized Optional<ProtosProcessStandardStreamValue> stdinForRuntime() {
        requireStandardStreamsEstablished();
        return stdinBinding == null
                ? Optional.empty()
                : Optional.of(stdinBinding.newViewForRuntime());
    }

    public synchronized Optional<ProtosProcessStandardStreamValue> stdoutForRuntime() {
        requireStandardStreamsEstablished();
        return stdoutBinding == null
                ? Optional.empty()
                : Optional.of(stdoutBinding.newViewForRuntime());
    }

    public synchronized Optional<ProtosProcessStandardStreamValue> stderrForRuntime() {
        requireStandardStreamsEstablished();
        return stderrBinding == null
                ? Optional.empty()
                : Optional.of(stderrBinding.newViewForRuntime());
    }

    /**
     * Establishes all three host-selected standard-stream Encoding associations exactly once.
     *
     * <p>An Encoding must exist exactly when its corresponding byte stream exists. Any mismatch is
     * stable INVALID bootstrap configuration; it is never repaired by a later host lookup,
     * inferred default, alias lookup, or codec discovery.
     */
    public synchronized void establishStandardStreamEncodingsForRuntime(
            ProtosEncodingValue stdinEncoding,
            ProtosEncodingValue stdoutEncoding,
            ProtosEncodingValue stderrEncoding) {
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process standard-stream Encodings cannot be established after termination begins");
        }
        requireStandardStreamsEstablished();
        if (stdinEncodingState != StandardStreamEncodingState.UNESTABLISHED
                || stdoutEncodingState != StandardStreamEncodingState.UNESTABLISHED
                || stderrEncodingState != StandardStreamEncodingState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process standard-stream Encoding bootstrap state is already established");
        }

        this.stdinEncodingState = encodingState(stdinBinding != null, stdinEncoding);
        this.stdoutEncodingState = encodingState(stdoutBinding != null, stdoutEncoding);
        this.stderrEncodingState = encodingState(stderrBinding != null, stderrEncoding);

        this.stdinEncoding =
                this.stdinEncodingState == StandardStreamEncodingState.AVAILABLE
                        ? stdinEncoding
                        : null;
        this.stdoutEncoding =
                this.stdoutEncodingState == StandardStreamEncodingState.AVAILABLE
                        ? stdoutEncoding
                        : null;
        this.stderrEncoding =
                this.stderrEncodingState == StandardStreamEncodingState.AVAILABLE
                        ? stderrEncoding
                        : null;
    }

    public synchronized StandardStreamEncodingState stdinEncodingStateForRuntime() {
        return stdinEncodingState;
    }

    public synchronized StandardStreamEncodingState stdoutEncodingStateForRuntime() {
        return stdoutEncodingState;
    }

    public synchronized StandardStreamEncodingState stderrEncodingStateForRuntime() {
        return stderrEncodingState;
    }

    public synchronized Optional<ProtosEncodingValue> stdinEncodingForRuntime() {
        requireEncodingAssociationsEstablished();
        return Optional.ofNullable(stdinEncoding);
    }

    public synchronized Optional<ProtosEncodingValue> stdoutEncodingForRuntime() {
        requireEncodingAssociationsEstablished();
        return Optional.ofNullable(stdoutEncoding);
    }

    public synchronized Optional<ProtosEncodingValue> stderrEncodingForRuntime() {
        requireEncodingAssociationsEstablished();
        return Optional.ofNullable(stderrEncoding);
    }

    private static StandardStreamEncodingState encodingState(
            boolean streamAvailable, ProtosEncodingValue encoding) {
        if (streamAvailable && encoding != null) {
            return StandardStreamEncodingState.AVAILABLE;
        }
        if (!streamAvailable && encoding == null) {
            return StandardStreamEncodingState.UNAVAILABLE;
        }
        return StandardStreamEncodingState.INVALID;
    }

    private void requireEncodingAssociationsEstablished() {
        if (stdinEncodingState == StandardStreamEncodingState.UNESTABLISHED
                || stdoutEncodingState == StandardStreamEncodingState.UNESTABLISHED
                || stderrEncodingState == StandardStreamEncodingState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process standard-stream Encoding bootstrap state is not established");
        }
    }

    private void requireStandardStreamsEstablished() {
        if (!standardStreamsEstablished) {
            throw new IllegalStateException(
                    "Process standard streams bootstrap state is not established");
        }
    }

    /**
     * Provisions one runtime-backed Process capability proxy for ordinary Actor delegation.
     *
     * <p>The wrapper itself is Actor-local state. Its only authority is the already-existing
     * logical Process represented by this runtime; provisioning does not create a Process and does
     * not manufacture filesystem, Node, Cluster, subprocess, or arbitrary host authority.
     */
    public synchronized ProtosProcessCapabilityValue provisionCapabilityForRuntime(
            ProtosObjectValue processPrototype) {
        Objects.requireNonNull(processPrototype, "processPrototype");
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process capability cannot be provisioned after termination begins");
        }
        return new ProtosProcessCapabilityValue(processPrototype, this);
    }

    /**
     * Creates one additional Actor hosted by this Process.
     *
     * <p>If Process termination already began while an existing non-preemptive Actor segment is
     * still running, the semantic Actor creation cutover may still occur. The new incarnation is
     * therefore registered and immediately receives the already-established Process termination
     * request instead of escaping into another failure domain.
     */
    public ProtosActor createHostedActorForRuntime(ProtosObjectValue actorRefPrototype) {
        Objects.requireNonNull(actorRefPrototype, "actorRefPrototype");
        ProtosActor actor;
        boolean terminateImmediately;
        synchronized (this) {
            if (lifecycle == LifecycleState.TERMINATED) {
                throw new IllegalStateException("terminated Process cannot host a new Actor");
            }
            actor = new ProtosActor(actorRefPrototype, this, false);
            liveActors.add(actor);
            terminateImmediately = lifecycle == LifecycleState.TERMINATING;
        }
        if (terminateImmediately) {
            actor.requestTerminationForRuntime();
        }
        return actor;
    }

    /** Internal failure-authority entry used only for an unhandled fatal Actor failure. */
    void actorFatalFailureForRuntime(ProtosActor actor, Object failure) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(failure, "failure");
        requireOwned(actor);
        if (actor != rootActor) {
            // Core v0.1 non-root failure authority has no automatic replacement/escalation.
            actor.requestTerminationForRuntime();
            return;
        }
        beginTermination(failure);
    }

    /** Authoritative local Process termination request; this is not an OS-process operation. */
    public boolean requestTerminationForRuntime() {
        return beginTermination(null);
    }

    private boolean beginTermination(Object rootFailure) {
        Set<ProtosActor> terminate;
        synchronized (this) {
            if (rootFailure != null && rootFailureCause == null) {
                rootFailureCause = rootFailure;
            }
            if (lifecycle != LifecycleState.RUNNING) {
                return false;
            }
            lifecycle = LifecycleState.TERMINATING;
            terminate = Set.copyOf(liveActors);
        }

        // Never hold the Process monitor while entering Actor termination. Actor cleanup can invoke
        // producer callbacks and, when complete, calls back into actorTerminatedForRuntime().
        for (ProtosActor actor : terminate) {
            actor.requestTerminationForRuntime();
        }
        tryCompleteTermination();
        return true;
    }

    void actorTerminatedForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        synchronized (this) {
            if (!liveActors.remove(actor)) {
                return;
            }
            if (lifecycle == LifecycleState.TERMINATING && liveActors.isEmpty()) {
                lifecycle = LifecycleState.TERMINATED;
                notifyAll();
            }
        }
    }

    private void tryCompleteTermination() {
        synchronized (this) {
            if (lifecycle == LifecycleState.TERMINATING && liveActors.isEmpty()) {
                lifecycle = LifecycleState.TERMINATED;
                notifyAll();
            }
        }
    }

    private void requireOwned(ProtosActor actor) {
        if (actor.processForRuntime().orElse(null) != this) {
            throw new IllegalArgumentException("Actor belongs to another Process runtime");
        }
    }

    synchronized int liveActorCountForTesting() {
        return liveActors.size();
    }

    synchronized Optional<Object> rootFailureCauseForTesting() {
        return Optional.ofNullable(rootFailureCause);
    }
}
