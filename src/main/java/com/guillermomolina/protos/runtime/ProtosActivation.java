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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ProtosActivation {
    private ProtosObjectValue context;
    private ProtosLexicalBindingAuthority deferredContextAuthority;
    private final ProtosLexicalEnvironment capturedEnvironment;
    private ProtosLexicalEnvironment currentEnvironment;
    private final Object receiver;
    private final ProtosPrelude prelude;
    private final ProtosArrayValue arguments;
    private final DeferredSuppliedArguments deferredSuppliedArguments;
    private final ProtosReturnHome returnHome;
    private final ProtosObjectValue methodHome;
    private final boolean ownsReturnHome;
    private final boolean construction;
    private final ProtosActorModuleState actorModuleState;
    private final ProtosModuleKey currentModuleKey;
    private final ProtosActorExecutionDomain executionDomain;
    private ProtosTask task;
    private ProtosIoOperation deferredCPrimeOperation;
    private ProtosIoReleaseExecution deferredCPrimeRelease;
    private ProtosDynamicControlState directDynamicControlState;

    private static final class DeferredSuppliedArguments {
        private final List<?> values;
        private ProtosArrayValue guestArray;

        private DeferredSuppliedArguments(List<?> values) {
            Objects.requireNonNull(values, "values");
            this.values =
                    values instanceof FrameBackedSuppliedArguments
                            ? values
                            : List.copyOf(values);
        }

        private List<?> values() {
            return values;
        }

        private synchronized ProtosArrayValue guestArray(ProtosPrelude prelude) {
            if (guestArray == null) {
                guestArray =
                        Objects.requireNonNull(prelude, "prelude")
                                .newFrozenArray(values);
            }
            return guestArray;
        }
    }

    /**
     * PERF025 read-only view of the supplied-argument range of a compact
     * source-call frame-argument array, adopted by a materialized activation
     * without copying. Valid only because that range is written once when the
     * call is prepared and never mutated afterwards.
     */
    private static final class FrameBackedSuppliedArguments
            extends java.util.AbstractList<Object>
            implements java.util.RandomAccess {
        private final Object[] frameArguments;
        private final int offset;

        private FrameBackedSuppliedArguments(Object[] frameArguments, int offset) {
            this.frameArguments = frameArguments;
            this.offset = offset;
        }

        @Override
        public Object get(int index) {
            Objects.checkIndex(index, size());
            // I091: a compact caller may pass a primitive carrier; observers see the guest value.
            return ProtosNumericValueSupport.guestValue(frameArguments[offset + index]);
        }

        @Override
        public int size() {
            return frameArguments.length - offset;
        }
    }

    /**
     * Internal PERF025 seam: the supplied values of a compact source-call
     * frame-argument array starting at {@code offset}, as a read-only view the
     * deferred invocation factories adopt without copying. The caller
     * guarantees that range is never mutated.
     */
    public static List<?> frameBackedSuppliedArgumentsForRuntime(
            Object[] frameArguments, int offset) {
        Objects.requireNonNull(frameArguments, "frameArguments");
        Objects.checkFromToIndex(offset, frameArguments.length, frameArguments.length);
        return new FrameBackedSuppliedArguments(frameArguments, offset);
    }

    public ProtosActivation(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver) {
        this(context, capturedLexicalContexts, receiver, null, null, null, null, false, false,
                new ProtosActorModuleState(), null, new ProtosActorExecutionDomain());
    }

    static ProtosActivation withPrelude(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver,
            ProtosPrelude prelude) {
        return new ProtosActivation(
                context,
                capturedLexicalContexts,
                receiver,
                Objects.requireNonNull(prelude, "prelude"),
                null,
                null,
                null,
                false,
                false,
                new ProtosActorModuleState(),
                null,
                new ProtosActorExecutionDomain());
    }

    static ProtosActivation withPreludeAndModuleState(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver,
            ProtosPrelude prelude,
            ProtosActorModuleState actorModuleState,
            ProtosModuleKey currentModuleKey,
            ProtosActorExecutionDomain executionDomain) {
        return new ProtosActivation(
                context, capturedLexicalContexts, receiver,
                Objects.requireNonNull(prelude, "prelude"), null, null, null, false, false,
                Objects.requireNonNull(actorModuleState, "actorModuleState"), currentModuleKey,
                Objects.requireNonNull(executionDomain, "executionDomain"));
    }

    public static ProtosActivation withReturnHome(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver,
            ProtosReturnHome returnHome) {
        return new ProtosActivation(
                context,
                capturedLexicalContexts,
                receiver,
                null,
                null,
                Objects.requireNonNull(returnHome, "returnHome"),
                null,
                false,
                false,
                new ProtosActorModuleState(),
                null,
                new ProtosActorExecutionDomain());
    }

    public static ProtosActivation withMethodHome(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver,
            ProtosObjectValue methodHome) {
        return new ProtosActivation(
                context,
                capturedLexicalContexts,
                receiver,
                null,
                null,
                null,
                Objects.requireNonNull(methodHome, "methodHome"),
                false,
                false,
                new ProtosActorModuleState(),
                null,
                new ProtosActorExecutionDomain());
    }

    public static ProtosActivation forClosureInvocation(
            ProtosClosureValue closure,
            java.util.List<?> supplied) {
        return forClosureInvocation(closure, supplied, null);
    }

    public static ProtosActivation forClosureInvocation(
            ProtosClosureValue closure,
            java.util.List<?> supplied,
            ProtosPrelude fallbackPrelude) {
        return forClosureInvocation(closure, supplied, fallbackPrelude, new ProtosActorModuleState(), null,
                new ProtosActorExecutionDomain());
    }

    public static ProtosActivation forClosureInvocation(
            ProtosClosureValue closure,
            java.util.List<?> supplied,
            ProtosPrelude fallbackPrelude,
            ProtosActorModuleState actorModuleState,
            ProtosModuleKey currentModuleKey) {
        return forClosureInvocation(closure, supplied, fallbackPrelude, actorModuleState, currentModuleKey,
                new ProtosActorExecutionDomain());
    }

    public static ProtosActivation forClosureInvocation(
            ProtosClosureValue closure,
            java.util.List<?> supplied,
            ProtosPrelude fallbackPrelude,
            ProtosActorModuleState actorModuleState,
            ProtosModuleKey currentModuleKey,
            ProtosActorExecutionDomain executionDomain) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(actorModuleState, "actorModuleState");

        ProtosPrelude prelude = closure.prelude().orElse(fallbackPrelude);
        if (prelude == null) {
            throw new IllegalStateException("Closure invocation requires an owning Core prelude");
        }
        boolean ownsReturnHome = closure.returnHome().isEmpty();
        ProtosReturnHome invocationHome = closure.invocationReturnHomeForRuntime();

        return new ProtosActivation(
                prelude.newExecutionContext(),
                closure.capturedLexicalEnvironmentForRuntime(),
                closure.capturedReceiver(),
                prelude,
                prelude.newFrozenArray(supplied),
                invocationHome,
                closure.methodHome().orElse(null),
                ownsReturnHome,
                false,
                actorModuleState,
                currentModuleKey,
                Objects.requireNonNull(executionDomain, "executionDomain"));
    }

    public static ProtosActivation forImmediateMethodInvocation(
            ProtosClosureValue closure,
            java.util.List<?> supplied,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosPrelude fallbackPrelude,
            ProtosActorModuleState actorModuleState,
            ProtosModuleKey currentModuleKey,
            ProtosActorExecutionDomain executionDomain) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(methodHome, "methodHome");
        Objects.requireNonNull(actorModuleState, "actorModuleState");

        ProtosPrelude prelude = closure.prelude().orElse(fallbackPrelude);
        if (prelude == null) {
            throw new IllegalStateException("Closure invocation requires an owning Core prelude");
        }
        boolean ownsReturnHome = closure.returnHome().isEmpty();
        ProtosReturnHome invocationHome = closure.invocationReturnHomeForRuntime();

        return new ProtosActivation(
                prelude.newExecutionContext(),
                closure.capturedLexicalEnvironmentForRuntime(),
                receiver,
                prelude,
                prelude.newFrozenArray(supplied),
                invocationHome,
                methodHome,
                ownsReturnHome,
                false,
                actorModuleState,
                currentModuleKey,
                Objects.requireNonNull(executionDomain, "executionDomain"));
    }

    /**
     * Internal PLAT040 frame-ABI materialization seam.
     *
     * <p>The invocation home is established before the Truffle call boundary so the
     * caller can retain exact non-local-return completion semantics. The rich
     * activation carrier is created only after target entry; its fresh guest
     * execution context and supplied guest Array remain deferred until semantics
     * actually observe them.
     */
    public static ProtosActivation forImmediateMethodInvocationWithReturnHomeForRuntime(
            ProtosClosureValue closure,
            java.util.List<?> supplied,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosPrelude fallbackPrelude,
            ProtosActorModuleState actorModuleState,
            ProtosModuleKey currentModuleKey,
            ProtosActorExecutionDomain executionDomain,
            ProtosReturnHome invocationHome) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(methodHome, "methodHome");
        return deferredInvocation(
                closure,
                supplied,
                receiver,
                methodHome,
                fallbackPrelude,
                actorModuleState,
                currentModuleKey,
                executionDomain,
                invocationHome);
    }

    /**
     * Internal PERF025 frame-ABI materialization seam for a direct source-backed
     * Closure invocation.
     *
     * <p>Exactly the activation {@link #forClosureInvocation} would build (the
     * Closure's captured receiver, captured method home, captured lexical
     * contexts and return-home ownership), except that it is created after
     * target entry with the fresh guest execution context and supplied guest
     * Array deferred until semantics observe them.
     */
    public static ProtosActivation forDirectClosureInvocationWithReturnHomeForRuntime(
            ProtosClosureValue closure,
            java.util.List<?> supplied,
            ProtosPrelude fallbackPrelude,
            ProtosActorModuleState actorModuleState,
            ProtosModuleKey currentModuleKey,
            ProtosActorExecutionDomain executionDomain,
            ProtosReturnHome invocationHome) {
        Objects.requireNonNull(closure, "closure");
        return deferredInvocation(
                closure,
                supplied,
                closure.capturedReceiver(),
                closure.methodHome().orElse(null),
                fallbackPrelude,
                actorModuleState,
                currentModuleKey,
                executionDomain,
                invocationHome);
    }

    private static ProtosActivation deferredInvocation(
            ProtosClosureValue closure,
            java.util.List<?> supplied,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosPrelude fallbackPrelude,
            ProtosActorModuleState actorModuleState,
            ProtosModuleKey currentModuleKey,
            ProtosActorExecutionDomain executionDomain,
            ProtosReturnHome invocationHome) {
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(actorModuleState, "actorModuleState");
        Objects.requireNonNull(invocationHome, "invocationHome");

        ProtosPrelude prelude = closure.prelude().orElse(fallbackPrelude);
        if (prelude == null) {
            throw new IllegalStateException("Closure invocation requires an owning Core prelude");
        }

        ProtosReturnHome capturedHome = closure.returnHome().orElse(null);
        boolean ownsReturnHome = capturedHome == null;
        if (!ownsReturnHome && capturedHome != invocationHome) {
            throw new IllegalArgumentException(
                    "compact invocation return home does not match the Closure capture");
        }

        return new ProtosActivation(
                null,
                closure.capturedLexicalEnvironmentForRuntime(),
                receiver,
                prelude,
                null,
                invocationHome,
                methodHome,
                ownsReturnHome,
                false,
                actorModuleState,
                currentModuleKey,
                Objects.requireNonNull(executionDomain, "executionDomain"),
                new DeferredSuppliedArguments(supplied));
    }

    private ProtosActivation(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver,
            ProtosPrelude prelude,
            ProtosArrayValue arguments,
            ProtosReturnHome returnHome,
            ProtosObjectValue methodHome,
            boolean ownsReturnHome,
            boolean construction,
            ProtosActorModuleState actorModuleState,
            ProtosModuleKey currentModuleKey,
            ProtosActorExecutionDomain executionDomain) {
        this(
                context,
                ProtosLexicalEnvironment.ofContexts(Objects.requireNonNull(
                        capturedLexicalContexts, "capturedLexicalContexts")),
                receiver,
                prelude,
                arguments,
                returnHome,
                methodHome,
                ownsReturnHome,
                construction,
                actorModuleState,
                currentModuleKey,
                executionDomain,
                null);
    }

    private ProtosActivation(
            ProtosObjectValue context,
            ProtosLexicalEnvironment capturedEnvironment,
            Object receiver,
            ProtosPrelude prelude,
            ProtosArrayValue arguments,
            ProtosReturnHome returnHome,
            ProtosObjectValue methodHome,
            boolean ownsReturnHome,
            boolean construction,
            ProtosActorModuleState actorModuleState,
            ProtosModuleKey currentModuleKey,
            ProtosActorExecutionDomain executionDomain) {
        this(
                context,
                capturedEnvironment,
                receiver,
                prelude,
                arguments,
                returnHome,
                methodHome,
                ownsReturnHome,
                construction,
                actorModuleState,
                currentModuleKey,
                executionDomain,
                null);
    }

    private ProtosActivation(
            ProtosObjectValue context,
            ProtosLexicalEnvironment capturedEnvironment,
            Object receiver,
            ProtosPrelude prelude,
            ProtosArrayValue arguments,
            ProtosReturnHome returnHome,
            ProtosObjectValue methodHome,
            boolean ownsReturnHome,
            boolean construction,
            ProtosActorModuleState actorModuleState,
            ProtosModuleKey currentModuleKey,
            ProtosActorExecutionDomain executionDomain,
            DeferredSuppliedArguments deferredSuppliedArguments) {
        if (context == null
                && (prelude == null || deferredSuppliedArguments == null)) {
            throw new NullPointerException(
                    "context may be deferred only for a compact invocation");
        }
        this.context = context;
        this.capturedEnvironment = capturedEnvironment;
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.prelude = prelude;
        this.arguments = arguments;
        this.deferredSuppliedArguments = deferredSuppliedArguments;
        this.returnHome = returnHome;
        this.methodHome = methodHome;
        this.ownsReturnHome = ownsReturnHome;
        this.construction = construction;
        this.actorModuleState = Objects.requireNonNull(actorModuleState, "actorModuleState");
        this.currentModuleKey = currentModuleKey;
        this.executionDomain = Objects.requireNonNull(executionDomain, "executionDomain");
    }

    public static ProtosActivation forObjectConstruction(
            ProtosObjectValue object,
            ProtosActivation enclosing) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(enclosing, "enclosing");
        ProtosActivation construction = new ProtosActivation(
                object,
                enclosing.lexicalEnvironmentForClosureCapture(),
                object,
                enclosing.prelude,
                enclosing.arguments,
                enclosing.returnHome,
                enclosing.methodHome,
                false,
                true,
                enclosing.actorModuleState,
                enclosing.currentModuleKey,
                enclosing.executionDomain,
                enclosing.deferredSuppliedArguments);
        if (enclosing.task().isPresent()) {
            construction.attachTask(enclosing.task().orElseThrow());
        } else {
            construction.inheritDynamicControlState(enclosing);
        }
        return construction;
    }

    public ProtosObjectValue context() {
        if (context == null) {
            ProtosLexicalBindingAuthority authority =
                    deferredContextAuthority;
            if (authority != null) {
                authority.prepareForContextObservation();
                context =
                        prelude.newExecutionContextForRuntime(
                                authority);
            } else {
                context = prelude.newExecutionContext();
            }
        }
        return context;
    }

    /**
     * I072-C backend seam: installs the current root's frame-backed lexical
     * authority without forcing the guest execution-context object to exist.
     * Repeated roots over one activation hand off the same authoritative
     * bindings exactly as a materialized execution context does.
     */
    public void installFrameLexicalBindingAuthorityForRuntime(
            ProtosLexicalBindingAuthority authority) {
        Objects.requireNonNull(authority, "authority");

        if (context != null) {
            if (context instanceof ProtosExecutionContextValue executionContext) {
                executionContext.installFrameLexicalBindingAuthority(authority);
                deferredContextAuthority = authority;
            }
            return;
        }

        ProtosLexicalBindingAuthority replaced = deferredContextAuthority;
        if (replaced != null && replaced != authority) {
            ProtosLexicalBindingAuthorityCalls.transferAll(replaced, authority);
        }

        deferredContextAuthority = authority;
        if (replaced != null && replaced != authority) {
            replaced.retireInstallation();
        }
    }

    /**
     * PERF025 frame-materialization slice: true while this activation's
     * current genuine execution context has neither been materialized as a
     * guest Context nor been given a lexical-binding authority. In that state
     * the executing root's frame locals are the only store its statically
     * proven current bindings can have, so such a root may establish them
     * directly in those locals while its frame is live. Any other state takes
     * the existing authority path.
     */
    public boolean hasUnobservedFrameNativeExecutionContextForRuntime() {
        return context == null && deferredContextAuthority == null;
    }

    /**
     * The lexical-binding authority currently installed for this activation's
     * execution context, or {@code null}. Backend-private; never materializes
     * the guest Context.
     */
    public ProtosLexicalBindingAuthority currentLexicalBindingAuthorityForRuntime() {
        return deferredContextAuthority;
    }

    /**
     * PERF037-D: this activation's guest Context when it already exists,
     * else {@code null}; never materializes it.
     */
    public ProtosObjectValue materializedContextOrNullForRuntime() {
        return context;
    }

    /**
     * True when the current lexical scope is semantically a genuine execution
     * context even if its guest object has not been materialized yet.
     */
    public boolean hasGenuineExecutionContextForRuntime() {
        return context == null || context instanceof ProtosExecutionContextValue;
    }

    /**
     * Reads current lexical membership without materializing the guest Context.
     */
    public boolean currentContextHasLocalSlotForRuntime(String name) {
        Objects.requireNonNull(name, "name");
        if (context != null) {
            return context.hasLocalSlot(name);
        }
        return deferredContextAuthority != null
                && ProtosLexicalBindingAuthorityCalls.contains(
                        deferredContextAuthority, name);
    }

    /**
     * Reads the current lexical scope's FROZEN state without materializing the
     * guest Context. Guest code can only freeze a materialized Context, so an
     * unmaterialized execution context is necessarily not FROZEN.
     */
    public boolean currentContextIsFrozenForRuntime() {
        return context != null && context.isFrozen();
    }

    /**
     * Reads the current lexical authority without materializing the guest Context.
     */
    public Optional<Object> readCurrentLocalSlotForRuntime(String name) {
        Objects.requireNonNull(name, "name");
        if (context != null) {
            return context.readLocalSlot(name);
        }
        if (deferredContextAuthority == null) {
            return Optional.empty();
        }
        return ProtosLexicalBindingAuthorityCalls.read(
                deferredContextAuthority, name);
    }

    /**
     * Establishes a current lexical binding directly in the single authoritative
     * store. An unmaterialized execution context is necessarily still OPEN.
     */
    public void createCurrentLocalSlotForRuntime(
            String name,
            Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");

        if (context != null) {
            context.createLocalSlot(name, value);
            return;
        }

        if (deferredContextAuthority == null) {
            context().createLocalSlot(name, value);
            return;
        }

        if (ProtosLexicalBindingAuthorityCalls.contains(
                deferredContextAuthority, name)) {
            throw new IllegalStateException(
                    "local slot already exists: " + name);
        }
        ProtosLexicalBindingAuthorityCalls.put(
                deferredContextAuthority, name, value);
    }

    /**
     * PERF025-H1 backend seam: the installed lexical-binding authority when a
     * current binding created now would take exactly its creation path, else
     * {@code null}, in which case only {@link #createCurrentLocalSlotForRuntime}
     * reproduces the exact creation rule. An unmaterialized execution context
     * is necessarily OPEN; a materialized one qualifies only while it is an
     * OPEN execution context whose own authority is still the installed one.
     * The caller must still reject a PRESENT binding. Nothing else of {@link
     * ProtosObjectValue#createLocalSlot} is bypassed: an execution context
     * never registers lookup dependencies. Never materializes the guest Context.
     */
    public ProtosLexicalBindingAuthority currentAuthorityAdmittingLocalCreationForRuntime() {
        ProtosLexicalBindingAuthority authority = deferredContextAuthority;
        if (authority == null || context == null) {
            return authority;
        }
        return context instanceof ProtosExecutionContextValue executionContext
                        && executionContext.isOpen()
                        && executionContext.lexicalBindingAuthorityForRuntime() == authority
                ? authority
                : null;
    }

    public void assignCurrentLocalSlotForRuntime(
            String name,
            Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");

        if (context != null) {
            context.assignLocalSlot(name, value);
            return;
        }

        if (deferredContextAuthority == null) {
            context().assignLocalSlot(name, value);
            return;
        }

        if (!ProtosLexicalBindingAuthorityCalls.contains(
                deferredContextAuthority, name)) {
            throw new IllegalStateException(
                    "local slot does not exist: " + name);
        }
        ProtosLexicalBindingAuthorityCalls.put(
                deferredContextAuthority, name, value);
    }

    /**
     * Cold compatibility projection of the captured lexical chain as context
     * objects; materializes every captured context. Runtime lookup paths use
     * {@link #capturedLexicalEnvironmentForRuntime()} instead.
     */
    public List<ProtosObjectValue> capturedLexicalContexts() {
        return ProtosLexicalEnvironment.contextsOf(capturedEnvironment);
    }

    /** Innermost captured lexical scope, or {@code null} when none is captured. */
    public ProtosLexicalEnvironment capturedLexicalEnvironmentForRuntime() {
        return capturedEnvironment;
    }

    /**
     * The current context's authority when the guest context is still
     * deferred, or the materialized execution context's own authority.
     */
    ProtosLexicalBindingAuthority deferredContextAuthorityForRuntime() {
        if (context != null) {
            return context instanceof ProtosExecutionContextValue executionContext
                    ? executionContext.lexicalBindingAuthorityForRuntime()
                    : null;
        }
        return deferredContextAuthority;
    }

    public Object receiver() {
        return receiver;
    }

    public Optional<ProtosPrelude> prelude() {
        return Optional.ofNullable(prelude);
    }

    public ProtosPrelude preludeOrNullForRuntime() {
        return prelude;
    }

    public ProtosActorModuleState actorModuleState() { return actorModuleState; }

    public Optional<ProtosModuleKey> currentModuleKey() { return Optional.ofNullable(currentModuleKey); }

    public ProtosActorExecutionDomain executionDomain() { return executionDomain; }

    /** Internal execution context used only by cooperative Actor-local task evaluation. */
    public Optional<ProtosTask> task() {
        return Optional.ofNullable(task);
    }

    /** PLAT029 private owner for non-Task deferred C-prime operation execution. */
    public Optional<ProtosIoOperation> deferredCPrimeOperationForRuntime() {
        return Optional.ofNullable(deferredCPrimeOperation);
    }

    /** PLAT030 private owner for non-Task lifecycle-release C-prime execution. */
    public Optional<ProtosIoReleaseExecution> deferredCPrimeReleaseForRuntime() {
        return Optional.ofNullable(deferredCPrimeRelease);
    }

    /** D121 authority is provenance-scoped, never inferred from TERMINATING alone. */
    public boolean terminationCleanupAuthorizedForRuntime() {
        if (deferredCPrimeRelease != null) return true;
        if (task == null) return false;
        return task.dynamicControlStateIfPresent()
                .map(ProtosDynamicControlState::hasActiveCancellationEnsureCleanupForRuntime)
                .orElse(false);
    }

    public synchronized ProtosDynamicControlState dynamicControlState() {
        if (task != null) {
            return task.dynamicControlState();
        }
        if (deferredCPrimeOperation != null) {
            return deferredCPrimeOperation.deferredCPrimeDynamicControlStateForRuntime();
        }
        if (deferredCPrimeRelease != null) {
            return deferredCPrimeRelease.deferredCPrimeDynamicControlStateForRuntime();
        }
        if (directDynamicControlState == null) {
            directDynamicControlState = new ProtosDynamicControlState();
        }
        return directDynamicControlState;
    }

    public synchronized Optional<ProtosDynamicControlState> dynamicControlStateIfPresent() {
        if (task != null) {
            return task.dynamicControlStateIfPresent();
        }
        if (deferredCPrimeOperation != null) {
            return deferredCPrimeOperation.deferredCPrimeDynamicControlStateIfPresentForRuntime();
        }
        if (deferredCPrimeRelease != null) {
            return deferredCPrimeRelease.deferredCPrimeDynamicControlStateIfPresentForRuntime();
        }
        return Optional.ofNullable(directDynamicControlState);
    }

    /*
     * TEST009-E: host boundary. Inheriting the enclosing dynamic control state reads
     * monitor-guarded Task/I-O state; it has no partial-evaluation value and, inlined,
     * its synchronized reads were expanded into every compiled call preparation.
     */
    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    public void inheritDynamicControlState(ProtosActivation enclosing) {
        Objects.requireNonNull(enclosing, "enclosing");
        Optional<ProtosIoReleaseExecution> inheritedRelease =
                enclosing.deferredCPrimeReleaseForRuntime();
        if (inheritedRelease.isPresent()) {
            attachDeferredCPrimeReleaseForRuntime(inheritedRelease.orElseThrow());
            return;
        }
        Optional<ProtosIoOperation> inheritedOperation =
                enclosing.deferredCPrimeOperationForRuntime();
        if (inheritedOperation.isPresent()) {
            attachDeferredCPrimeOperationForRuntime(inheritedOperation.orElseThrow());
            return;
        }
        Optional<ProtosDynamicControlState> inherited =
                enclosing.dynamicControlStateIfPresent();
        if (inherited.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (task != null) {
                return;
            }
            ProtosDynamicControlState state = inherited.orElseThrow();
            if (directDynamicControlState != null && directDynamicControlState != state) {
                throw new IllegalStateException(
                        "activation already belongs to another direct dynamic-control flow");
            }
            directDynamicControlState = state;
        }
    }

    /** Attaches this activation to exactly one Actor-local task. */
    public void attachTask(ProtosTask task) {
        Objects.requireNonNull(task, "task");
        if (deferredCPrimeOperation != null) {
            throw new IllegalStateException(
                    "operation-owned C-prime activation cannot acquire Task identity");
        }
        if (deferredCPrimeRelease != null) {
            throw new IllegalStateException(
                    "release-owned C-prime activation cannot acquire Task identity");
        }
        if (this.task != null && this.task != task) {
            throw new IllegalStateException("activation already belongs to another task");
        }
        this.task = task;
    }

    /** Attaches this activation to exactly one non-Task PLAT029 I/O operation. */
    public void attachDeferredCPrimeOperationForRuntime(ProtosIoOperation operation) {
        Objects.requireNonNull(operation, "operation");
        if (task != null) {
            throw new IllegalStateException(
                    "Task-owned activation cannot acquire operation C-prime identity");
        }
        if (deferredCPrimeRelease != null) {
            throw new IllegalStateException(
                    "release-owned C-prime activation cannot acquire operation C-prime identity");
        }
        if (operation.origin().executionDomain() != executionDomain) {
            throw new IllegalArgumentException(
                    "deferred C-prime operation belongs to another Actor execution domain");
        }
        if (deferredCPrimeOperation != null && deferredCPrimeOperation != operation) {
            throw new IllegalStateException(
                    "activation already belongs to another deferred C-prime operation");
        }
        if (directDynamicControlState != null) {
            throw new IllegalStateException(
                    "direct dynamic-control activation cannot become operation-owned");
        }
        deferredCPrimeOperation = operation;
    }

    /** Attaches this activation to exactly one non-Task PLAT030 lifecycle release. */
    public void attachDeferredCPrimeReleaseForRuntime(ProtosIoReleaseExecution release) {
        Objects.requireNonNull(release, "release");
        if (task != null) throw new IllegalStateException("Task-owned activation cannot acquire release C-prime identity");
        if (deferredCPrimeOperation != null) throw new IllegalStateException("operation-owned C-prime activation cannot acquire release C-prime identity");
        if (release.origin().executionDomain() != executionDomain) throw new IllegalArgumentException("deferred C-prime release belongs to another Actor execution domain");
        if (deferredCPrimeRelease != null && deferredCPrimeRelease != release) throw new IllegalStateException("activation already belongs to another deferred C-prime release");
        if (directDynamicControlState != null) throw new IllegalStateException("direct dynamic-control activation cannot become release-owned");
        deferredCPrimeRelease = release;
    }

    public Optional<ProtosArrayValue> arguments() {
        if (arguments != null) {
            return Optional.of(arguments);
        }
        if (deferredSuppliedArguments == null) {
            return Optional.empty();
        }
        return Optional.of(deferredSuppliedArguments.guestArray(prelude));
    }

    public List<?> suppliedArgumentsForRuntime() {
        if (deferredSuppliedArguments != null) {
            return deferredSuppliedArguments.values();
        }
        if (arguments != null) {
            return arguments.indexedSnapshot();
        }
        throw new IllegalStateException(
                "parameter binding requires an invocation activation");
    }

    /**
     * PERF032-G6: the number of supplied arguments of this invocation
     * activation, read without snapshotting them.
     */
    public int suppliedArgumentCountForRuntime() {
        if (deferredSuppliedArguments != null) {
            return deferredSuppliedArguments.values().size();
        }
        if (arguments != null) {
            return arguments.indexedSize();
        }
        throw new IllegalStateException(
                "parameter binding requires an invocation activation");
    }

    public Optional<ProtosReturnHome> returnHome() {
        return Optional.ofNullable(returnHome);
    }

    public Optional<ProtosObjectValue> methodHome() {
        return Optional.ofNullable(methodHome);
    }

    public boolean ownsReturnHome() {
        return ownsReturnHome;
    }

    /**
     * PERF025: the lexical environment a Closure materialized in this
     * activation captures, by reference and without materializing any guest
     * context. All Closures created here share the same node, so they observe
     * one context identity once anything does observe it.
     *
     * <p>An object body is not a lexical capture scope
     * ({@code EXECUTION_AND_CONTROL.md} §4): a construction activation hands
     * on its enclosing environment unchanged.
     */
    public ProtosLexicalEnvironment lexicalEnvironmentForClosureCapture() {
        if (construction) {
            return capturedEnvironment;
        }
        ProtosLexicalEnvironment current = currentEnvironment;
        if (current == null) {
            current = context != null
                    ? ProtosLexicalEnvironment.materialized(context, capturedEnvironment)
                    : ProtosLexicalEnvironment.deferred(this, capturedEnvironment);
            currentEnvironment = current;
        }
        return current;
    }

    /**
     * Cold compatibility projection of {@link #lexicalEnvironmentForClosureCapture()}
     * as context objects; materializes the whole chain.
     */
    public List<ProtosObjectValue> lexicalContextsForClosureCapture() {
        return ProtosLexicalEnvironment.contextsOf(
                lexicalEnvironmentForClosureCapture());
    }

}
