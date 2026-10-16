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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.CanonicalNonSuspensionAnalysis.Precondition;
import com.guillermomolina.protos.execution.CanonicalNonSuspensionAnalysis.Proof;
import com.guillermomolina.protos.execution.CanonicalNonSuspensionAnalysis.Result;
import com.guillermomolina.protos.execution.CanonicalNonSuspensionAnalysis.SelectedSend;
import com.guillermomolina.protos.execution.CanonicalNonSuspensionAnalysis.SendSelection;
import com.guillermomolina.protos.execution.CanonicalNonSuspensionAnalysis.Subject;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * I107 (PLAT061-B, D204-A): compile-time contract of the compositional
 * non-suspension analysis and of its facts in {@link CanonicalCompilationFacts}.
 * No guest code is executed: every proof is derived from the canonical tree and
 * its lexical analysis only.
 */
class ProtosI107NonSuspensionFactsTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void literalsParametersAndEmptyBodiesAreProven() {
        for (String source : List.of(
                "() => { }", "() => { 1 }", "() => { null\n \"text\"\n true }",
                "(value) => { value }", "(first, second) => { second\n first }")) {
            assertEquals(Proof.PROVEN_NO_SUSPENSION, canonicalBody(source).proof(), source);
            assertEquals(Proof.PROVEN_NO_SUSPENSION, effectiveRoot(source).proof(), source);
        }
    }

    @Test
    void longSafeSequencesAreProvenInLinearWork() {
        StringBuilder body = new StringBuilder();
        int statements = 600;
        for (int index = 0; index < statements; index++) {
            body.append("v").append(index).append(": ")
                    .append(index == 0 ? "1" : "v" + (index - 1)).append('\n');
        }
        body.append("v").append(statements - 1);
        CanonicalClosure closure = closure("() => {\n" + body + "\n}");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalNonSuspensionAnalysis subject = new CanonicalCompilationFacts()
                .newAnalysis(analysis, scope, closure, SendSelection.NONE);

        Result result = subject.proveBody(closure.body(), closure);

        assertTrue(result.proven());
        assertTrue(result.preconditions().contains(Precondition.DIRECT_FRAME_LOCALS));
        // Every node visited exactly once: creations, their values and the final read.
        assertEquals(2 * statements + 1, subject.analyzedNodeCount());
    }

    @Test
    void identityComposesOverProvenOperands() {
        assertTrue(canonicalBody("(a, b) => { a === b }").proven());
        assertTrue(canonicalBody("(a) => { a !== null }").proven());
        assertFalse(canonicalBody("(a) => { a.size() === 1 }").proven());
    }

    @Test
    void localCreationsAndReadsNeedEstablishedPresence() {
        assertTrue(canonicalBody("() => { x: 1\n x = 2\n x }").proven());
        // A read before the creation does not establish presence from the declaration.
        assertFalse(canonicalBody("() => { x\n x: 1 }").proven());
        // A captured read is not an own established binding.
        CanonicalSequence program = canonicalize("x: 1\n(() => { x })");
        CanonicalClosure inner =
                assertInstanceOf(CanonicalClosure.class, program.expressions().get(1));
        CanonicalBindingAnalysis global = CanonicalBindingAnalyzer.analyzeModule(program);
        assertFalse(new CanonicalCompilationFacts()
                .canonicalBodyNonSuspension(inner.body(), inner, global,
                        global.scopeOf(inner).orElseThrow())
                .proven());
    }

    @Test
    void closureConstructionIsNotExecutionOfItsBody() {
        // The nested body calls an unknown value, but it is only constructed.
        assertTrue(canonicalBody("() => { () => { unknown() } }").proven());
        assertFalse(canonicalBody("() => { (() => { 1 })() }").proven());
    }

    @Test
    void dynamicOperationsRemainUnknown() {
        for (String source : List.of(
                "(f) => { f() }", "(o) => { o.message() }", "(a) => { a + 1 }",
                "(items) => { items.size() }", "(f, items) => { f(...items) }",
                "(f) => { f.value() }", "() => { context }")) {
            Result result = canonicalBody(source);
            assertEquals(Proof.UNKNOWN, result.proof(), source);
            assertTrue(result.preconditions().isEmpty(), source);
        }
    }

    @Test
    void recursionAndTransitiveEffectsRemainUnknown() {
        assertFalse(canonicalBody("() => { self: () => { self() }\n self() }").proven());
        assertFalse(canonicalBody("() => { f: () => { 1 }\n f() }").proven());
    }

    @Test
    void safeBodyWithUnknownDefaultHasUnknownEntry() {
        Result body = canonicalBody("(value: compute()) => { 1 }");
        Result root = effectiveRoot("(value: compute()) => { 1 }");
        assertTrue(body.proven());
        assertEquals(Subject.CANONICAL_BODY, body.subject());
        assertFalse(root.proven());
        assertEquals(Subject.EFFECTIVE_ROOT, root.subject());

        // A default that is itself proven keeps the entry proven.
        assertTrue(effectiveRoot("(value: 7) => { value }").proven());
        assertTrue(effectiveRoot("(x: 1, y) => { y }").proven());
        // Rest binding and an observable ReturnHome are not part of this proof.
        assertFalse(effectiveRoot("(first: 7, ...tail) => { first }").proven());
        assertFalse(effectiveRoot("() => { escape: () => { ^1 }\n escape()\n 0 }").proven());
    }

    @Test
    void selectedDefaultsAreAnalyzedOnlyWhenTheyMayRun() {
        // A default reading an earlier parameter is established; a later one is not.
        assertTrue(effectiveRoot("(a, b: a) => { b }").proven());
        assertFalse(effectiveRoot("(a: b, b) => { a }").proven());
    }

    @Test
    void conditionalCompositionNeedsCompleteSelection() {
        CanonicalClosure closure = closure(
                "() => {\n true.ifTrue() { 1 }\n false.ifFalse() { 2 }\n"
                        + " true.ifTrueIfFalse(() => { 3 }, () => { unknown() })\n 4\n}");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        // No selection producer: every send is unknown.
        assertFalse(facts.newAnalysis(analysis, scope, closure, SendSelection.NONE)
                .proveBody(closure.body(), closure).proven());

        // A producer proving the literal selections: only the selected bodies run.
        SendSelection literalSelection = literalReceiverSelection();
        Result proven = facts.newAnalysis(analysis, scope, closure, literalSelection)
                .proveBody(closure.body(), closure);
        assertTrue(proven.proven(), proven::toString);
        assertTrue(proven.preconditions().contains(Precondition.NORMAL_PROFILE));
        assertTrue(proven.preconditions().contains(Precondition.STANDARD_OBJECT_PUBLISHED));

        // The other path of the same table is unsafe once selected.
        CanonicalClosure unsafe = closure("() => { false.ifTrueIfFalse(() => { 3 }, () => { unknown() }) }");
        CanonicalBindingAnalysis unsafeAnalysis = CanonicalBindingAnalyzer.analyzeClosure(unsafe);
        assertFalse(facts.newAnalysis(
                        unsafeAnalysis, unsafeAnalysis.scopeOf(unsafe).orElseThrow(), unsafe,
                        literalSelection)
                .proveBody(unsafe.body(), unsafe).proven());
    }

    @Test
    void nestedControlDepthIsNotArtificiallyUnknown() {
        String inner = "1";
        for (int depth = 0; depth < 40; depth++) {
            inner = "true.ifTrue() { " + inner + " }";
        }
        CanonicalClosure closure = closure("() => { " + inner + " }");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        assertTrue(new CanonicalCompilationFacts()
                .newAnalysis(analysis, analysis.scopeOf(closure).orElseThrow(), closure,
                        literalReceiverSelection())
                .proveBody(closure.body(), closure).proven());
    }

    @Test
    void subjectsAreDistinctAndNeverImplyEachOther() {
        CanonicalClosure closure = closure("(value: compute()) => { value }");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        Result body = facts.canonicalBodyNonSuspension(
                closure.body(), closure, analysis, scope);
        assertTrue(body.proven());
        for (Subject other : List.of(
                Subject.EFFECTIVE_ROOT, Subject.COMPLETE_INVOCATION, Subject.GUARDED_CALL_SITE)) {
            assertTrue(facts.queryNonSuspension(other, closure.body(), analysis, scope).isEmpty());
            assertFalse(facts.consumeNonSuspension(other, closure.body(), analysis, scope).proven());
        }
        // A guarded proof records its guard and is not a complete invocation.
        Result guarded = new Result(Proof.PROVEN_NO_SUSPENSION, Subject.GUARDED_CALL_SITE,
                closure, analysis, scope, EnumSet.of(Precondition.RUNTIME_SELECTION_GUARD),
                "test guard");
        assertEquals(CanonicalCompilationFacts.Publication.RECORDED,
                facts.publishNonSuspension(guarded));
        assertFalse(facts.queryNonSuspension(Subject.COMPLETE_INVOCATION, closure, analysis, scope)
                .map(Result::proven).orElse(false));
    }

    @Test
    void unknownIsRefinedByALaterProducerAndConsumedByAnotherPhase() {
        CanonicalClosure closure = closure("() => { 1 }");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();
        CanonicalCompilationFacts.EffectiveRoot root =
                facts.effectiveRoot(closure, analysis, scope, null);

        // Planner (before the builder) has no proof.
        assertEquals(CanonicalCompilationFacts.Publication.RECORDED, facts.publishNonSuspension(
                Result.unknown(Subject.EFFECTIVE_ROOT, root, analysis, scope, "planner")));
        // Lowerer transformation refines it with its own provenance.
        Result refined = new Result(Proof.PROVEN_NO_SUSPENSION, Subject.EFFECTIVE_ROOT, root,
                analysis, scope, EnumSet.of(Precondition.NORMAL_PROFILE), "lowerer fold");
        assertEquals(CanonicalCompilationFacts.Publication.REFINED,
                facts.publishNonSuspension(refined));
        // A later UNKNOWN never downgrades it.
        assertEquals(CanonicalCompilationFacts.Publication.UNCHANGED, facts.publishNonSuspension(
                Result.unknown(Subject.EFFECTIVE_ROOT, root, analysis, scope, "late")));
        // The root publisher consumes the refined view.
        Result consumed = facts.consumeNonSuspension(Subject.EFFECTIVE_ROOT, root, analysis, scope);
        assertSame(refined, consumed);
        assertEquals("lowerer fold", consumed.provenance());
    }

    @Test
    void contradictionsAndInvalidationAreRejectedExplicitly() {
        CanonicalClosure closure = closure("() => { 1 }");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();
        Object identity = facts.effectiveRoot(closure, analysis, scope, null);

        Result first = new Result(Proof.PROVEN_NO_SUSPENSION, Subject.EFFECTIVE_ROOT, identity,
                analysis, scope, EnumSet.of(Precondition.NORMAL_PROFILE), "a");
        Result other = new Result(Proof.PROVEN_NO_SUSPENSION, Subject.EFFECTIVE_ROOT, identity,
                analysis, scope, EnumSet.of(Precondition.DIRECT_FRAME_LOCALS), "b");
        assertEquals(CanonicalCompilationFacts.Publication.RECORDED, facts.publishNonSuspension(first));
        assertEquals(CanonicalCompilationFacts.Publication.REJECTED_INCOMPATIBLE,
                facts.publishNonSuspension(other));
        assertSame(first, facts.queryNonSuspension(
                Subject.EFFECTIVE_ROOT, identity, analysis, scope).orElseThrow());

        facts.consumeNonSuspension(Subject.EFFECTIVE_ROOT, identity, analysis, scope);
        assertEquals(CanonicalCompilationFacts.Publication.REJECTED_CONSUMED,
                facts.invalidateNonSuspension(
                        Subject.EFFECTIVE_ROOT, identity, analysis, scope, "test"));

        // An unconsumed proof can be invalidated, and then rejects new proofs.
        Object second = new Object();
        Result proof = new Result(Proof.PROVEN_NO_SUSPENSION, Subject.EFFECTIVE_ROOT, second,
                analysis, scope, Set.of(), "c");
        facts.publishNonSuspension(proof);
        assertEquals(CanonicalCompilationFacts.Publication.INVALIDATED,
                facts.invalidateNonSuspension(Subject.EFFECTIVE_ROOT, second, analysis, scope, "x"));
        assertEquals(CanonicalCompilationFacts.Publication.REJECTED_INVALIDATED,
                facts.publishNonSuspension(proof));
        assertFalse(facts.consumeNonSuspension(Subject.EFFECTIVE_ROOT, second, analysis, scope)
                .proven());

        // A consumed UNKNOWN is not silently refined afterwards.
        Object third = new Object();
        facts.consumeNonSuspension(Subject.EFFECTIVE_ROOT, third, analysis, scope);
        assertEquals(CanonicalCompilationFacts.Publication.REJECTED_CONSUMED,
                facts.publishNonSuspension(new Result(Proof.PROVEN_NO_SUSPENSION,
                        Subject.EFFECTIVE_ROOT, third, analysis, scope, Set.of(), "late")));
    }

    @Test
    void canonicalBodyCacheCannotImportControlSelectionAuthority() {
        CanonicalClosure closure = closure("() => { true.ifTrue() { 1 } }");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        Result canonical = facts.canonicalBodyNonSuspension(
                closure.body(), closure, analysis, scope);
        assertFalse(canonical.proven());
        assertEquals(Proof.UNKNOWN, canonical.proof());

        Result selected = facts.newAnalysis(analysis, scope, closure,
                literalReceiverSelection()).proveBody(closure.body(), closure);
        assertTrue(selected.proven());

        assertSame(canonical, facts.canonicalBodyNonSuspension(
                closure.body(), closure, analysis, scope));
        assertSame(canonical, facts.queryNonSuspension(
                Subject.CANONICAL_BODY, closure.body(), analysis, scope).orElseThrow());

        CanonicalCompilationFacts reversed = new CanonicalCompilationFacts();
        assertTrue(reversed.newAnalysis(analysis, scope, closure,
                literalReceiverSelection()).proveBody(closure.body(), closure).proven());

        Result inverse = reversed.canonicalBodyNonSuspension(
                closure.body(), closure, analysis, scope);
        assertFalse(inverse.proven());
        assertSame(inverse, reversed.canonicalBodyNonSuspension(
                closure.body(), closure, analysis, scope));
    }

    @Test
    void nestedCallbackSelectionReceivesItsOwnLexicalScope() {
        CanonicalClosure root = closure(
                "() => { true.ifTrue() { false.ifFalse() { 1 } } }");
        CanonicalBindingAnalysis rootAnalysis =
                CanonicalBindingAnalyzer.analyzeClosure(root);
        CanonicalLexicalScope rootScope = rootAnalysis.scopeOf(root).orElseThrow();
        CanonicalSend outer = assertInstanceOf(
                CanonicalSend.class, root.body().expressions().get(0));
        CanonicalClosure callback = assertInstanceOf(
                CanonicalClosure.class, outer.arguments().get(0));
        CanonicalSend inner = assertInstanceOf(
                CanonicalSend.class, callback.body().expressions().get(0));

        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();
        CanonicalBindingAnalysis callbackAnalysis =
                facts.isolatedClosureAnalysis(callback);
        CanonicalLexicalScope callbackScope =
                callbackAnalysis.scopeOf(callback).orElseThrow();

        java.util.IdentityHashMap<CanonicalSend, CanonicalBindingAnalysis> analyses =
                new java.util.IdentityHashMap<>();
        java.util.IdentityHashMap<CanonicalSend, CanonicalLexicalScope> scopes =
                new java.util.IdentityHashMap<>();

        SendSelection literal = literalReceiverSelection();
        SendSelection recording = (send, ownerAnalysis, ownerScope) -> {
            analyses.put(send, ownerAnalysis);
            scopes.put(send, ownerScope);
            return literal.select(send, ownerAnalysis, ownerScope);
        };

        assertTrue(facts.newAnalysis(rootAnalysis, rootScope, root, recording)
                .proveBody(root.body(), root).proven());
        assertSame(rootAnalysis, analyses.get(outer));
        assertSame(rootScope, scopes.get(outer));
        assertSame(callbackAnalysis, analyses.get(inner));
        assertSame(callbackScope, scopes.get(inner));
        assertNotSame(rootScope, scopes.get(inner));
    }

    @Test
    void rejectedPublicationNeverAuthorizesAReplacement() {
        CanonicalClosure closure = closure("() => { 1 }");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();
        Object identity = facts.effectiveRoot(closure, analysis, scope, null);

        Result first = new Result(
                Proof.PROVEN_NO_SUSPENSION, Subject.EFFECTIVE_ROOT,
                identity, analysis, scope,
                EnumSet.of(Precondition.NORMAL_PROFILE), "accepted");
        Result rejected = new Result(
                Proof.PROVEN_NO_SUSPENSION, Subject.EFFECTIVE_ROOT,
                identity, analysis, scope,
                EnumSet.of(Precondition.DIRECT_FRAME_LOCALS), "rejected");

        assertEquals(CanonicalCompilationFacts.Publication.RECORDED,
                facts.publishNonSuspension(first));
        assertEquals(CanonicalCompilationFacts.Publication.REJECTED_INCOMPATIBLE,
                facts.publishNonSuspension(rejected));

        assertSame(first, facts.consumeNonSuspension(
                Subject.EFFECTIVE_ROOT, identity, analysis, scope));
        assertEquals(CanonicalCompilationFacts.Publication.REJECTED_INCOMPATIBLE,
                facts.publishNonSuspension(rejected));
        assertSame(first, facts.queryNonSuspension(
                Subject.EFFECTIVE_ROOT, identity, analysis, scope).orElseThrow());
    }

    @Test
    void repeatedQueriesReuseOneResultByIdentity() {
        CanonicalClosure closure = closure("(value) => { value }");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        Result first = facts.canonicalBodyNonSuspension(
                closure.body(), closure, analysis, scope);
        assertSame(first, facts.canonicalBodyNonSuspension(
                closure.body(), closure, analysis, scope));
        assertSame(facts.effectiveRoot(closure, analysis, scope, null),
                facts.effectiveRoot(closure, analysis, scope, null));
    }

    @Test
    void analysesScopesAndEqualDefinitionsAreIsolated() {
        CanonicalSequence program = canonicalize("(value) => { value }");
        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalBindingAnalysis global = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();
        CanonicalBindingAnalysis isolated = facts.isolatedClosureAnalysis(closure);

        Result globalResult = facts.canonicalBodyNonSuspension(closure.body(), closure, global,
                global.scopeOf(closure).orElseThrow());
        Result isolatedResult = facts.canonicalBodyNonSuspension(closure.body(), closure,
                isolated, isolated.scopeOf(closure).orElseThrow());
        assertNotSame(globalResult, isolatedResult);
        assertSame(global, globalResult.analysis());
        assertSame(isolated, isolatedResult.analysis());

        // A scope the definition does not own proves nothing.
        assertFalse(facts.canonicalBodyNonSuspension(
                closure.body(), closure, global, global.topScope()).proven());

        // Structurally equal definitions never share results or effective identities.
        CanonicalClosure twin = closure("(value) => { value }");
        CanonicalBindingAnalysis twinAnalysis = facts.isolatedClosureAnalysis(twin);
        CanonicalLexicalScope twinScope = twinAnalysis.scopeOf(twin).orElseThrow();
        assertEquals(closure, twin);
        assertNotSame(
                facts.effectiveRoot(closure, isolated, isolated.scopeOf(closure).orElseThrow(), null),
                facts.effectiveRoot(twin, twinAnalysis, twinScope, null));
        assertTrue(facts.queryNonSuspension(
                Subject.CANONICAL_BODY, twin.body(), twinAnalysis, twinScope).isEmpty());
    }

    @Test
    void reparsedTreesAndDifferentTransformationsGetNewIdentities() {
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();
        CanonicalClosure original = closure("() => { true.ifTrue() { 1 } }");
        CanonicalBindingAnalysis analysis = facts.isolatedClosureAnalysis(original);
        CanonicalLexicalScope scope = analysis.scopeOf(original).orElseThrow();
        CanonicalCompilationFacts.EffectiveRoot unfolded =
                facts.effectiveRoot(original, analysis, scope, null);
        CanonicalLiteral literal = assertInstanceOf(CanonicalLiteral.class,
                assertInstanceOf(CanonicalClosure.class,
                        assertInstanceOf(CanonicalSend.class, original.body().expressions().get(0))
                                .arguments().get(0)).body().expressions().get(0));
        CanonicalCompilationFacts.EffectiveRoot folded =
                facts.effectiveRoot(original, analysis, scope, literal);
        assertNotSame(unfolded, folded);
        assertSame(literal, folded.foldedTerminal());

        CanonicalClosure reparsed = closure("() => { true.ifTrue() { 1 } }");
        CanonicalBindingAnalysis rematerialized = analysis.rematerializeForReparsedClosure(reparsed);
        CanonicalLexicalScope reparsedScope = rematerialized.scopeOf(reparsed).orElseThrow();
        assertNotSame(folded, facts.effectiveRoot(reparsed, rematerialized, reparsedScope, literal));
        assertFalse(facts.canonicalBodyNonSuspension(
                original.body(), original, rematerialized, reparsedScope)
                .proven());
    }

    @Test
    void factsOwnersNeverShareProofs() {
        CanonicalClosure closure = closure("(value) => { value }");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        // One owner per lowerer, and therefore per profile and Context.
        CanonicalCompilationFacts normal = new CanonicalCompilationFacts();
        CanonicalCompilationFacts debug = new CanonicalCompilationFacts();
        normal.canonicalBodyNonSuspension(closure.body(), closure, analysis, scope);
        assertTrue(debug.queryNonSuspension(
                Subject.CANONICAL_BODY, closure.body(), analysis, scope).isEmpty());
        assertNotSame(normal.effectiveRoot(closure, analysis, scope, null),
                debug.effectiveRoot(closure, analysis, scope, null));
    }

    @Test
    void createIdentityIsTheOneOwnedByTheScope() {
        CanonicalClosure closure = closure("() => { x: 1\n x }");
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalCreate create =
                assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(0));
        assertSame(analysis.scopeOf(closure).orElseThrow(),
                analysis.identityOf(create).orElseThrow().owner());
        assertTrue(canonicalBody("() => { x: 1\n x }").proven());
    }

    /** A stand-in for the lowerer's selection producer (literal Boolean receivers). */
    private static SendSelection literalReceiverSelection() {
        return (send, analysis, scope) -> {
            if (!(send.receiver() instanceof CanonicalLiteral receiver)
                    || !send.arguments().stream().allMatch(CanonicalClosure.class::isInstance)) {
                return null;
            }
            boolean value = receiver.kind() == CanonicalLiteral.Kind.TRUE;
            int selected = switch (send.message()) {
                case "ifTrue" -> value ? 0 : -1;
                case "ifFalse" -> value ? -1 : 0;
                case "ifTrueIfFalse" -> value ? 0 : 1;
                default -> -2;
            };
            if (selected == -2) {
                return null;
            }
            return new SelectedSend(
                    List.of(),
                    selected < 0
                            ? List.of()
                            : List.of((CanonicalClosure) send.arguments().get(selected)),
                    EnumSet.of(Precondition.NORMAL_PROFILE, Precondition.STANDARD_OBJECT_PUBLISHED));
        };
    }

    private Result canonicalBody(String source) {
        CanonicalClosure closure = closure(source);
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        return new CanonicalCompilationFacts().canonicalBodyNonSuspension(
                closure.body(), closure, analysis, analysis.scopeOf(closure).orElseThrow());
    }

    private Result effectiveRoot(String source) {
        CanonicalClosure closure = closure(source);
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();
        return facts.newAnalysis(analysis, scope, closure, SendSelection.NONE)
                .proveEffectiveRoot(
                        facts.effectiveRoot(closure, analysis, scope, null),
                        closure,
                        closure.body().expressions(),
                        facts.mayObserveReturnHome(closure),
                        "test");
    }

    private CanonicalClosure closure(String source) {
        return assertInstanceOf(CanonicalClosure.class, canonicalize(source).expressions().get(0));
    }

    private CanonicalSequence canonicalize(String source) {
        return (CanonicalSequence) canonicalizer.canonicalize(new ProtosParser(source).parseProgram());
    }
}
