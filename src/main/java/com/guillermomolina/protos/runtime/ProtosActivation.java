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
    private final List<ProtosObjectValue> capturedLexicalContexts;
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
            this.values = List.copyOf(Objects.requireNonNull(values, "values"));
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
        ProtosReturnHome capturedHome = closure.returnHome().orElse(null);
        boolean ownsReturnHome = capturedHome == null;
        ProtosReturnHome invocationHome =
                ownsReturnHome ? new ProtosReturnHome() : capturedHome;

        return new ProtosActivation(
                prelude.newExecutionContext(),
                closure.capturedLexicalContexts(),
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
        ProtosReturnHome capturedHome = closure.returnHome().orElse(null);
        boolean ownsReturnHome = capturedHome == null;
        ProtosReturnHome invocationHome =
                ownsReturnHome ? new ProtosReturnHome() : capturedHome;

        return new ProtosActivation(
                prelude.newExecutionContext(),
                closure.capturedLexicalContexts(),
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
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(methodHome, "methodHome");
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
                closure.capturedLexicalContexts(),
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
                capturedLexicalContexts,
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
            ProtosActorExecutionDomain executionDomain,
            DeferredSuppliedArguments deferredSuppliedArguments) {
        if (context == null
                && (prelude == null || deferredSuppliedArguments == null)) {
            throw new NullPointerException(
                    "context may be deferred only for a compact invocation");
        }
        this.context = context;
        this.capturedLexicalContexts =
                List.copyOf(Objects.requireNonNull(
                        capturedLexicalContexts, "capturedLexicalContexts"));
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
                enclosing.lexicalContextsForClosureCapture(),
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
            if (deferredContextAuthority != null) {
                deferredContextAuthority.prepareForContextObservation();
            }
            ProtosExecutionContextValue materialized =
                    (ProtosExecutionContextValue) prelude.newExecutionContext();
            if (deferredContextAuthority != null) {
                materialized.installFrameLexicalBindingAuthority(
                        deferredContextAuthority);
            }
            context = materialized;
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

        if (deferredContextAuthority != null
                && deferredContextAuthority != authority) {
            java.util.ArrayList<String> existingNames =
                    new java.util.ArrayList<>();
            java.util.ArrayList<Object> existingValues =
                    new java.util.ArrayList<>();
            deferredContextAuthority.appendBindingsTo(
                    existingNames,
                    existingValues);

            for (int index = 0; index < existingNames.size(); index++) {
                authority.putBinding(
                        existingNames.get(index),
                        existingValues.get(index));
            }
        }

        deferredContextAuthority = authority;
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
                && deferredContextAuthority.containsBinding(name);
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
        return deferredContextAuthority.readBinding(name);
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

        if (deferredContextAuthority.containsBinding(name)) {
            throw new IllegalStateException(
                    "local slot already exists: " + name);
        }
        deferredContextAuthority.putBinding(name, value);
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

        if (!deferredContextAuthority.containsBinding(name)) {
            throw new IllegalStateException(
                    "local slot does not exist: " + name);
        }
        deferredContextAuthority.putBinding(name, value);
    }

    public List<ProtosObjectValue> capturedLexicalContexts() {
        return capturedLexicalContexts;
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

    public Optional<ProtosReturnHome> returnHome() {
        return Optional.ofNullable(returnHome);
    }

    public Optional<ProtosObjectValue> methodHome() {
        return Optional.ofNullable(methodHome);
    }

    public boolean ownsReturnHome() {
        return ownsReturnHome;
    }

    public List<ProtosObjectValue> lexicalContextsForClosureCapture() {
        if (construction) {
            return capturedLexicalContexts;
        }

        int capturedCount = capturedLexicalContexts.size();
        java.util.ArrayList<ProtosObjectValue> contexts =
                new java.util.ArrayList<>(1 + capturedCount);
        contexts.add(context());
        for (int index = 0; index < capturedCount; index++) {
            contexts.add(capturedLexicalContexts.get(index));
        }
        return java.util.Collections.unmodifiableList(contexts);
    }

}
