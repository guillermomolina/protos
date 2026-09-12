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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosMatchBytecodeExecutionTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path MATCHING =
            Path.of("protos", "tests", "conformance", "matching");

    @Test
    void nonStructuralMatchCorpusHasBytecodeParity() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

                for (String name : List.of(
                        "value-order-subject-matcher-once.protos",
                        "binder-wildcard.protos",
                        "opaque-captures-fixed-rest.protos",
                        "or-dynamic-captures.protos",
                        "or-nested-attempt-order.protos",
                        "or-ordered-effects-first-success.protos",
                        "or-same-subject.protos",
                        "or-subject-evaluated-once.protos",
                        "guard-bindings-shared-with-body.protos",
                        "guard-dynamic-rest-visible.protos",
                        "guard-false-next-arm-effects.protos",
                        "guard-non-local-return.protos",
                        "guard-or-success-not-reopened.protos",
                        "guard-pattern-mismatch-skips-guard.protos",
                        "guard-true-body-once.protos")) {
                    assertSame(
                            ProtosBooleanValue.TRUE,
                            executeFixture(language, prelude, name),
                            name);
                }

                /*
                 * D096: alias-of-binder is syntactically irrefutable, so this
                 * alias ABI check deliberately has no later arm. A fallback
                 * arm here would be invalid source and would test the parser,
                 * not the Bytecode backend.
                 */
                String alias =
                        "marker: {}\n"
                                + "marker match {\n"
                                + "    case @whole: (@inner) => whole === inner\n"
                                + "}\n";
                assertSame(
                        ProtosBooleanValue.TRUE,
                        executeSource(
                                language,
                                prelude.newModuleActivation(),
                                alias,
                                "i038-d7a-alias.protos"));
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void bytecodePreservesFreshErrorAndNoRetryContracts() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

                String noMatch = source("no-match-error.protos");
                ProtosSignalException first =
                        assertThrows(
                                ProtosSignalException.class,
                                () ->
                                        executeSource(
                                                language,
                                                prelude.newModuleActivation(),
                                                noMatch,
                                                "no-match-error.protos"));
                ProtosSignalException second =
                        assertThrows(
                                ProtosSignalException.class,
                                () ->
                                        executeSource(
                                                language,
                                                prelude.newModuleActivation(),
                                                noMatch,
                                                "no-match-error-2.protos"));
                assertSame(prelude.errorPrototype(), first.error().parent().orElseThrow());
                assertSame(prelude.errorPrototype(), second.error().parent().orElseThrow());
                assertNotSame(first.error(), second.error());

                for (String name : List.of(
                        "invalid-outcome-error.protos",
                        "empty-capture-array-error.protos",
                        "or-invalid-outcome-no-retry.protos",
                        "guard-invalid-no-retry-error.protos",
                        "guard-terminal-no-match-error.protos")) {
                    ProtosSignalException signalled =
                            assertThrows(
                                    ProtosSignalException.class,
                                    () ->
                                            executeSource(
                                                    language,
                                                    prelude.newModuleActivation(),
                                                    source(name),
                                                    name));
                    assertSame(
                            prelude.errorPrototype(),
                            signalled.error().parent().orElseThrow(),
                            name);
                }

                ProtosActivation activation = prelude.newModuleActivation();
                ProtosSignalException arity =
                        assertThrows(
                                ProtosSignalException.class,
                                () ->
                                        executeSource(
                                                language,
                                                activation,
                                                source("capture-arity-no-retry-error.protos"),
                                                "capture-arity-no-retry-error.protos"));
                assertSame(prelude.errorPrototype(), arity.error().parent().orElseThrow());
                ProtosObjectValue state =
                        (ProtosObjectValue) activation.lookup("state").orElseThrow();
                ProtosIntegerValue later =
                        (ProtosIntegerValue) state.readLocalSlot("later").orElseThrow();
                assertEquals(BigInteger.ZERO, later.value());
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void matcherGuardAndBodySuspendInsideOneCPrimeContinuationChain()
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActivation module = prelude.newModuleActivation();
                ProtosActorExecutionDomain domain = module.executionDomain();

                Dependency matcherDependency = new Dependency();
                Dependency guardDependency = new Dependency();
                Dependency bodyDependency = new Dependency();
                AtomicInteger matcherCalls = new AtomicInteger();
                AtomicInteger guardCalls = new AtomicInteger();
                AtomicInteger bodyCalls = new AtomicInteger();

                ProtosObjectValue matcher =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                matcher.createLocalSlot(
                        "match",
                        controlledSuspendingLeaf(
                                matcherDependency,
                                ProtosBooleanValue.TRUE,
                                1,
                                matcherCalls));
                module.context().createLocalSlot("matcher", matcher);
                module.context().createLocalSlot(
                        "guard",
                        controlledSuspendingLeaf(
                                guardDependency,
                                ProtosBooleanValue.TRUE,
                                0,
                                guardCalls));
                module.context().createLocalSlot(
                        "bodyPause",
                        controlledSuspendingLeaf(
                                bodyDependency,
                                ProtosNullValue.INSTANCE,
                                0,
                                bodyCalls));

                String characters =
                        "marker: {}\n"
                                + "marker match {\n"
                                + "    case matcher when guard() => {\n"
                                + "        bodyPause()\n"
                                + "        42\n"
                                + "    }\n"
                                + "    case _ => 99\n"
                                + "}\n";
                RootCallTarget target =
                        compileBytecode(
                                language,
                                characters,
                                "i038-d7a-cprime-suspension.protos");

                ProtosTask task =
                        domain.createTask(
                                null,
                                current ->
                                        ProtosBytecodeTaskExecution.execute(
                                                current,
                                                target,
                                                module));

                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(1, matcherCalls.get());
                assertEquals(0, guardCalls.get());
                assertEquals(0, bodyCalls.get());

                assertTrue(matcherDependency.complete());
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(1, matcherCalls.get(), "matcher must not replay");
                assertEquals(1, guardCalls.get());
                assertEquals(0, bodyCalls.get());

                assertTrue(guardDependency.complete());
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(1, matcherCalls.get());
                assertEquals(1, guardCalls.get(), "guard must not replay");
                assertEquals(1, bodyCalls.get());

                assertTrue(bodyDependency.complete());
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                ProtosIntegerValue result =
                        (ProtosIntegerValue) task.result().orElseThrow();
                assertEquals(BigInteger.valueOf(42), result.value());
                assertEquals(1, matcherCalls.get());
                assertEquals(1, guardCalls.get());
                assertEquals(1, bodyCalls.get(), "body must not replay");
            } finally {
                context.leave();
            }
        }
    }


    @Test
    void arrayMatchCorpusHasBytecodeParity() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

                for (String name : List.of(
                        "array-fixed-alias.protos",
                        "array-shape-ineligible.protos",
                        "array-snapshot-child-order.protos",
                        "array-remainder-bindings.protos",
                        "array-empty-remainder.protos",
                        "array-nested-dynamic-terminal.protos",
                        "array-effects-retained-on-mismatch.protos")) {
                    assertSame(
                            ProtosBooleanValue.TRUE,
                            executeFixture(language, prelude, name),
                            name);
                }

                ProtosArrayValue pair =
                        (ProtosArrayValue)
                                executeFixture(
                                        language,
                                        prelude,
                                        "array-remainder-identity.protos");
                List<Object> values = pair.indexedSnapshot();
                assertEquals(2, values.size());
                ProtosArrayValue first = (ProtosArrayValue) values.get(0);
                ProtosArrayValue second = (ProtosArrayValue) values.get(1);
                assertTrue(first.isFrozen());
                assertTrue(second.isFrozen());
                assertSame(prelude.arrayPrototype(), first.parent().orElseThrow());
                assertSame(prelude.arrayPrototype(), second.parent().orElseThrow());
                assertNotSame(first, second);
                assertEquals(1, first.indexedSnapshot().size());
                assertEquals(1, second.indexedSnapshot().size());
                assertSame(
                        first.indexedSnapshot().get(0),
                        second.indexedSnapshot().get(0));
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void arraySnapshotSurvivesSuspendingChildWithoutReplay() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActivation module = prelude.newModuleActivation();
                ProtosActorExecutionDomain domain = module.executionDomain();
                Dependency dependency = new Dependency();
                AtomicInteger matcherCalls = new AtomicInteger();

                ProtosObjectValue pausePattern =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                pausePattern.createLocalSlot(
                        "match",
                        controlledSuspendingLeaf(
                                dependency,
                                ProtosBooleanValue.TRUE,
                                1,
                                matcherCalls));
                module.context().createLocalSlot("pausePattern", pausePattern);

                String characters =
                        "oldMiddle: {}\n"
                                + "oldLast: {}\n"
                                + "replacementMiddle: {}\n"
                                + "replacementLast: {}\n"
                                + "subject: Array(1, oldMiddle, oldLast)\n"
                                + "subject match {\n"
                                + "    case [pausePattern, ...@middle, @last] =>\n"
                                + "        (middle.size() == 1) &&\n"
                                + "        (middle[0] === oldMiddle) &&\n"
                                + "        (last === oldLast)\n"
                                + "    case _ => false\n"
                                + "}\n";
                RootCallTarget target =
                        compileBytecode(
                                language,
                                characters,
                                "i038-d7b-array-snapshot-suspension.protos");
                ProtosTask task =
                        domain.createTask(
                                null,
                                current ->
                                        ProtosBytecodeTaskExecution.execute(
                                                current,
                                                target,
                                                module));

                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(1, matcherCalls.get());

                ProtosArrayValue subject =
                        (ProtosArrayValue) module.lookup("subject").orElseThrow();
                Object replacementMiddle =
                        module.lookup("replacementMiddle").orElseThrow();
                Object replacementLast =
                        module.lookup("replacementLast").orElseThrow();
                subject.indexedPut(BigInteger.ONE, replacementMiddle);
                subject.indexedPut(BigInteger.valueOf(2), replacementLast);

                assertTrue(dependency.complete());
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertSame(ProtosBooleanValue.TRUE, task.result().orElseThrow());
                assertEquals(1, matcherCalls.get(), "Array child matcher must not replay");
            } finally {
                context.leave();
            }
        }
    }


    @Test
    void mapMatchCorpusHasBytecodeParity() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

                for (String name : List.of(
                        "map-open-exact.protos",
                        "map-key-order-stable-snapshot.protos",
                        "map-all-keys-before-children.protos",
                        "map-repeated-requirements.protos",
                        "map-exact-residue-before-children.protos",
                        "map-ineligible.protos",
                        "map-remainder-bindings.protos",
                        "map-nested-dynamic-terminal.protos")) {
                    assertSame(
                            ProtosBooleanValue.TRUE,
                            executeFixture(language, prelude, name),
                            name);
                }

                ProtosArrayValue result =
                        (ProtosArrayValue)
                                executeFixture(
                                        language,
                                        prelude,
                                        "map-remainder-identity.protos");
                List<Object> values = result.indexedSnapshot();
                assertEquals(2, values.size());
                ProtosMapValue first = (ProtosMapValue) values.get(0);
                ProtosMapValue second = (ProtosMapValue) values.get(1);
                assertTrue(first.isFrozen());
                assertTrue(second.isFrozen());
                assertSame(prelude.mapPrototype(), first.parent().orElseThrow());
                assertSame(prelude.mapPrototype(), second.parent().orElseThrow());
                assertNotSame(first, second);

                List<ProtosMapValue.Entry> firstEntries = first.keyedSnapshot();
                List<ProtosMapValue.Entry> secondEntries = second.keyedSnapshot();
                assertEquals(1, firstEntries.size());
                assertEquals(1, secondEntries.size());
                assertSame(firstEntries.get(0).key(), secondEntries.get(0).key());
                assertSame(firstEntries.get(0).value(), secondEntries.get(0).value());
                assertEquals(
                        firstEntries.get(0).recordedHash(),
                        secondEntries.get(0).recordedHash());
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void mapSnapshotAndCPrimeSurviveSuspendingQueryHashEqualityAndChild()
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActivation module = prelude.newModuleActivation();
                ProtosActorExecutionDomain domain = module.executionDomain();

                Dependency queryDependency = new Dependency();
                Dependency hashDependency = new Dependency();
                Dependency equalityDependency = new Dependency();
                Dependency childDependency = new Dependency();
                AtomicInteger queryCalls = new AtomicInteger();
                AtomicInteger hashCalls = new AtomicInteger();
                AtomicInteger equalityCalls = new AtomicInteger();
                AtomicInteger childCalls = new AtomicInteger();

                ProtosObjectValue storedKey =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue oldValue =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue secondKey =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue secondValue =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue replacementValue =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue lateKey =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue lateValue =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());

                ProtosMapValue subject = prelude.newMap();
                subject.append(storedKey, BigInteger.ONE, oldValue);
                subject.append(secondKey, BigInteger.valueOf(2), secondValue);
                module.context().createLocalSlot("subject", subject);

                ProtosObjectValue queryKey =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                queryKey.createLocalSlot(
                        "hash",
                        controlledSuspendingLeaf(
                                hashDependency,
                                new ProtosIntegerValue(BigInteger.ONE),
                                0,
                                hashCalls));
                queryKey.createLocalSlot(
                        "==",
                        controlledSuspendingLeaf(
                                equalityDependency,
                                ProtosBooleanValue.TRUE,
                                1,
                                equalityCalls));
                module.context().createLocalSlot(
                        "queryFactory",
                        controlledSuspendingLeaf(
                                queryDependency,
                                queryKey,
                                0,
                                queryCalls));

                ProtosObjectValue childPattern =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                childPattern.createLocalSlot(
                        "match",
                        controlledSuspendingMatcher(
                                childDependency,
                                oldValue,
                                childCalls));
                module.context().createLocalSlot("childPattern", childPattern);

                String characters =
                        "subject match {\n"
                                + "    case %{ queryFactory(): childPattern, ...@rest } =>\n"
                                + "        rest.size() == 1\n"
                                + "    case _ => false\n"
                                + "}\n";
                RootCallTarget target =
                        compileBytecode(
                                language,
                                characters,
                                "i038-d7c-map-cprime-snapshot.protos");
                ProtosTask task =
                        domain.createTask(
                                null,
                                current ->
                                        ProtosBytecodeTaskExecution.execute(
                                                current,
                                                target,
                                                module));

                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(1, queryCalls.get());
                assertEquals(0, hashCalls.get());
                assertEquals(0, equalityCalls.get());
                assertEquals(0, childCalls.get());
                assertEquals(false, subject.comparisonActive());

                ProtosMapValue.Entry firstAssociation = subject.keyedSnapshot().get(0);
                subject.replaceValue(firstAssociation, replacementValue);
                subject.append(lateKey, BigInteger.valueOf(3), lateValue);

                assertTrue(queryDependency.complete());
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(1, queryCalls.get(), "query expression must not replay");
                assertEquals(1, hashCalls.get());
                assertEquals(0, equalityCalls.get());
                assertEquals(0, childCalls.get());
                assertTrue(subject.comparisonActive());

                assertTrue(hashDependency.complete());
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(1, queryCalls.get());
                assertEquals(1, hashCalls.get(), "hash must not replay");
                assertEquals(1, equalityCalls.get());
                assertEquals(0, childCalls.get());
                assertTrue(subject.comparisonActive());

                assertTrue(equalityDependency.complete());
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                assertEquals(1, queryCalls.get());
                assertEquals(1, hashCalls.get());
                assertEquals(1, equalityCalls.get(), "equality must not replay");
                assertEquals(1, childCalls.get());
                assertEquals(false, subject.comparisonActive());

                assertTrue(childDependency.complete());
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertSame(ProtosBooleanValue.TRUE, task.result().orElseThrow());
                assertEquals(1, queryCalls.get());
                assertEquals(1, hashCalls.get());
                assertEquals(1, equalityCalls.get());
                assertEquals(1, childCalls.get(), "mapped-value child must not replay");
            } finally {
                context.leave();
            }
        }
    }

    private static Object executeFixture(
            ProtosLanguage language,
            ProtosPrelude prelude,
            String name)
            throws Exception {
        return executeSource(
                language,
                prelude.newModuleActivation(),
                source(name),
                name);
    }

    private static Object executeSource(
            ProtosLanguage language,
            ProtosActivation activation,
            String characters,
            String sourceName)
            throws Exception {
        return compileBytecode(language, characters, sourceName).call(activation);
    }

    private static RootCallTarget compileBytecode(
            ProtosLanguage language,
            String characters,
            String sourceName)
            throws Exception {
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                characters,
                                sourceName)
                        .build();
        return new ProtosSourceCompiler().compileBytecode(source, language);
    }

    private static String source(String name) throws Exception {
        return Files.readString(MATCHING.resolve(name));
    }

    private static ProtosClosureValue controlledSuspendingLeaf(
            Dependency dependency,
            Object resumedResult,
            int expectedArity,
            AtomicInteger calls) {
        return ProtosClosureValue.suspensionCapableNativeClosure(
                (activation, supplied) -> {
                    throw new AssertionError(
                            "I038 matching suspension evidence escaped the C-prime native path");
                },
                (activation, supplied) -> {
                    assertEquals(expectedArity, supplied.size());
                    calls.incrementAndGet();
                    ProtosTask task = activation.task().orElseThrow();
                    dependency.register(task);
                    return ProtosNativeSuspension.pending(
                            dependency,
                            () -> resumedResult);
                });
    }


    private static ProtosClosureValue controlledSuspendingMatcher(
            Dependency dependency,
            Object expectedSubject,
            AtomicInteger calls) {
        return ProtosClosureValue.suspensionCapableNativeClosure(
                (activation, supplied) -> {
                    throw new AssertionError(
                            "I038 matching suspension evidence escaped the C-prime native path");
                },
                (activation, supplied) -> {
                    assertEquals(1, supplied.size());
                    assertSame(expectedSubject, supplied.get(0));
                    calls.incrementAndGet();
                    ProtosTask task = activation.task().orElseThrow();
                    dependency.register(task);
                    return ProtosNativeSuspension.pending(
                            dependency,
                            () -> ProtosBooleanValue.TRUE);
                });
    }

    private static final class Dependency implements ProtosTask.WaitDependency {
        private volatile ProtosTask task;
        private volatile boolean ready;

        void register(ProtosTask owner) {
            task = owner;
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public void waitingTaskCancelled(ProtosTask cancelled) {
            // Cancellation is outside this focal; the method is required by the protocol.
        }

        boolean complete() {
            if (task == null) {
                throw new IllegalStateException(
                        "dependency completed before registration");
            }
            ready = true;
            return task.resume(this);
        }
    }
}
