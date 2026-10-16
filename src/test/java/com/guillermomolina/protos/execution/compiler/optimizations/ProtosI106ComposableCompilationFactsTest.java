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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * I106 (PLAT061-B): the compilation-unit-local {@link CanonicalCompilationFacts}
 * memoizes canonical proofs by the exact identity of their definition,
 * analysis and scope, keeps isolated and whole-tree analyses apart, and never
 * turns missing proof into a positive one. These are compile-time checks; no
 * guest code is executed.
 */
class ProtosI106ComposableCompilationFactsTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void repeatedRootQueriesShareOneImmutableProof() {
        CanonicalSequence program = canonicalize("(value) => { value }");
        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        CanonicalCompilationFacts.RootFacts first =
                facts.rootFacts(closure.body(), closure, analysis, scope);
        CanonicalCompilationFacts.RootFacts second =
                facts.rootFacts(closure.body(), closure, analysis, scope);

        assertSame(first, second);
        assertSame(analysis, first.bindingAnalysis());
        assertSame(scope, first.scope());
        assertFalse(first.requiresPersistentFrameAuthority());

        CanonicalParameter parameter = closure.parameters().get(0);
        CanonicalBindingIdentity parameterIdentity =
                analysis.identityOf(parameter).orElseThrow();
        CanonicalLookup read =
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(0));
        CanonicalBindingResolution.Resolved resolved =
                assertInstanceOf(
                        CanonicalBindingResolution.Resolved.class,
                        analysis.resolutionOf(read).orElseThrow());
        assertSame(scope, parameterIdentity.owner());
        assertSame(parameterIdentity, resolved.identity());
        assertEquals(java.util.Set.of("value"), scope.declaredNames());
    }

    @Test
    void missingOrNegativeProofKeepsPersistentFrameAuthority() {
        assertPersistent("() => { context }");
        assertPersistent("() => { () => { 1 } }");
        assertPersistent("() => { x\n x: 1 }");

        CanonicalSequence program = canonicalize("(value) => { value }");
        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        assertTrue(
                facts.rootFacts(closure.body(), closure, null, null)
                        .requiresPersistentFrameAuthority());

        CanonicalBindingAnalysis unrelated =
                CanonicalBindingAnalyzer.analyzeModule(canonicalize("(value) => { value }"));
        assertTrue(unrelated.scopeOf(closure).isEmpty());
        assertTrue(
                facts.rootFacts(closure.body(), closure, unrelated, unrelated.topScope())
                        .requiresPersistentFrameAuthority());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalCompilationFacts.RootFacts module =
                facts.rootFacts(program, null, analysis, analysis.topScope());
        assertTrue(module.requiresPersistentFrameAuthority());
        assertSame(module, facts.rootFacts(program, null, analysis, analysis.topScope()));
    }

    @Test
    void isolatedAnalysisIsMemoizedAndNeverPosesAsWholeTreeCapture() {
        CanonicalSequence program = canonicalize("x: 1\n(() => { x })");
        CanonicalCreate outer =
                assertInstanceOf(CanonicalCreate.class, program.expressions().get(0));
        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, program.expressions().get(1));
        CanonicalLookup read =
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(0));
        CanonicalBindingAnalysis global = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        CanonicalBindingAnalysis isolated = facts.isolatedClosureAnalysis(closure);
        assertSame(isolated, facts.isolatedClosureAnalysis(closure));
        assertNotSame(global, isolated);
        assertNotSame(
                global.scopeOf(closure).orElseThrow(),
                isolated.scopeOf(closure).orElseThrow());

        CanonicalBindingIdentity outerIdentity = global.identityOf(outer).orElseThrow();
        CanonicalBindingResolution.CapturedResolved captured =
                assertInstanceOf(
                        CanonicalBindingResolution.CapturedResolved.class,
                        global.resolutionOf(read).orElseThrow());
        assertSame(outerIdentity, captured.identity());

        assertTrue(isolated.identityOf(outer).isEmpty());
        assertFalse(
                isolated.resolutionOf(read).orElse(null)
                                instanceof CanonicalBindingResolution.CapturedResolved isolatedCapture
                        && isolatedCapture.identity() == outerIdentity);

        CanonicalCompilationFacts.RootFacts globalFacts =
                facts.rootFacts(
                        closure.body(), closure, global, global.scopeOf(closure).orElseThrow());
        CanonicalCompilationFacts.RootFacts isolatedFacts =
                facts.rootFacts(
                        closure.body(),
                        closure,
                        isolated,
                        isolated.scopeOf(closure).orElseThrow());
        assertNotSame(globalFacts, isolatedFacts);
        assertSame(global, globalFacts.bindingAnalysis());
        assertSame(isolated, isolatedFacts.bindingAnalysis());
    }

    @Test
    void returnHomeObservabilityBelongsToTheDefinition() {
        CanonicalClosure local =
                assertInstanceOf(
                        CanonicalClosure.class,
                        canonicalize("() => { 1 }").expressions().get(0));
        CanonicalClosure nonLocal =
                assertInstanceOf(
                        CanonicalClosure.class,
                        canonicalize("() => { escape: () => { ^1 }\n escape()\n 0 }")
                                .expressions()
                                .get(0));
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        assertFalse(facts.mayObserveReturnHome(local));
        assertTrue(facts.mayObserveReturnHome(nonLocal));
        assertFalse(facts.mayObserveReturnHome(local));
        assertTrue(facts.mayObserveReturnHome(nonLocal));
        assertEquals(
                CanonicalReturnHomeAnalysis.mayObserveReturnHome(nonLocal),
                facts.mayObserveReturnHome(nonLocal));
    }

    @Test
    void callCandidatesKeepExactIdentitiesAndNeverPromoteResolution() {
        CanonicalSequence program =
                canonicalize(
                        """
                        run: () => {
                            f: (value) => { value }
                            f(1)
                            g: (value) => { value }
                            g: 2
                            g(3)
                            h(4)
                        }
                        """);
        CanonicalCreate run =
                assertInstanceOf(CanonicalCreate.class, program.expressions().get(0));
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, run.value());
        CanonicalSequence body = closure.body();
        CanonicalCreate createF = assertInstanceOf(CanonicalCreate.class, body.expressions().get(0));
        CanonicalCall callF = assertInstanceOf(CanonicalCall.class, body.expressions().get(1));
        CanonicalCall callG = assertInstanceOf(CanonicalCall.class, body.expressions().get(4));
        CanonicalCall callH = assertInstanceOf(CanonicalCall.class, body.expressions().get(5));
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        Map<CanonicalCall, CanonicalClosure> candidates =
                facts.bindingClosureCallCandidates(body, closure, analysis, scope);

        assertSame(candidates, facts.bindingClosureCallCandidates(body, closure, analysis, scope));
        assertEquals(1, candidates.size());
        assertSame(createF.value(), candidates.get(callF));
        assertFalse(candidates.containsKey(callG));
        assertFalse(candidates.containsKey(callH));
        assertThrows(
                UnsupportedOperationException.class,
                () -> candidates.put(callG, closure));

        // The map is only an expectation; the receiver resolutions are untouched.
        CanonicalLookup dynamicReceiver = assertInstanceOf(CanonicalLookup.class, callH.receiver());
        assertFalse(
                analysis.resolutionOf(dynamicReceiver).orElse(null)
                        instanceof CanonicalBindingResolution.Resolved);
        assertInstanceOf(
                CanonicalBindingResolution.Resolved.class,
                analysis.resolutionOf(
                                assertInstanceOf(CanonicalLookup.class, callF.receiver()))
                        .orElseThrow());

        CanonicalBindingAnalysis isolated = facts.isolatedClosureAnalysis(closure);
        assertNotSame(
                candidates,
                facts.bindingClosureCallCandidates(
                        body, closure, isolated, isolated.scopeOf(closure).orElseThrow()));
    }

    @Test
    void structurallyEqualOrReparsedDefinitionsNeverShareProofs() {
        String source = "(value) => { value }";
        CanonicalClosure original =
                assertInstanceOf(CanonicalClosure.class, canonicalize(source).expressions().get(0));
        CanonicalClosure reparsed =
                assertInstanceOf(CanonicalClosure.class, canonicalize(source).expressions().get(0));
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        CanonicalBindingAnalysis originalAnalysis = facts.isolatedClosureAnalysis(original);
        CanonicalBindingAnalysis reparsedIsolated = facts.isolatedClosureAnalysis(reparsed);
        assertNotSame(originalAnalysis, reparsedIsolated);

        CanonicalCompilationFacts.RootFacts originalFacts =
                facts.rootFacts(
                        original.body(),
                        original,
                        originalAnalysis,
                        originalAnalysis.scopeOf(original).orElseThrow());

        CanonicalBindingAnalysis rematerialized =
                originalAnalysis.rematerializeForReparsedClosure(reparsed);
        assertTrue(rematerialized.scopeOf(original).isEmpty());
        CanonicalLexicalScope reparsedScope = rematerialized.scopeOf(reparsed).orElseThrow();
        CanonicalCompilationFacts.RootFacts reparsedFacts =
                facts.rootFacts(reparsed.body(), reparsed, rematerialized, reparsedScope);

        assertNotSame(originalFacts, reparsedFacts);
        assertSame(rematerialized, reparsedFacts.bindingAnalysis());
        assertSame(reparsedScope, reparsedFacts.scope());
        assertNotSame(originalFacts.scope(), reparsedFacts.scope());
        assertFalse(reparsedFacts.requiresPersistentFrameAuthority());

        // A proof for the original definition is not valid under the new analysis.
        assertTrue(
                facts.rootFacts(original.body(), original, rematerialized, reparsedScope)
                        .requiresPersistentFrameAuthority());
    }

    @Test
    void mismatchedBodyOrScopeQueriesFailClosed() {
        CanonicalSequence program =
                canonicalize(
                        """
                        run: () => {
                            f: (value) => { value }
                            f(1)
                        }
                        (other) => { other }
                        """);
        CanonicalClosure closure =
                assertInstanceOf(
                        CanonicalClosure.class,
                        assertInstanceOf(CanonicalCreate.class, program.expressions().get(0))
                                .value());
        CanonicalClosure frameNative =
                assertInstanceOf(CanonicalClosure.class, program.expressions().get(1));
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();
        CanonicalLexicalScope frameNativeScope = analysis.scopeOf(frameNative).orElseThrow();
        CanonicalCompilationFacts facts = new CanonicalCompilationFacts();

        // The unrelated body is itself proven frame-native under its own definition.
        assertFalse(
                facts.rootFacts(frameNative.body(), frameNative, analysis, frameNativeScope)
                        .requiresPersistentFrameAuthority());

        // Invalid body: that frame-native body queried as another definition's root.
        CanonicalCompilationFacts.RootFacts invalidBody =
                facts.rootFacts(frameNative.body(), closure, analysis, scope);
        assertTrue(invalidBody.requiresPersistentFrameAuthority());
        // Invalid scope: the valid body under a scope its definition does not own.
        assertTrue(
                facts.rootFacts(frameNative.body(), frameNative, analysis, scope)
                        .requiresPersistentFrameAuthority());
        assertTrue(
                facts.rootFacts(
                                frameNative.body(), frameNative, analysis, analysis.topScope())
                        .requiresPersistentFrameAuthority());
        // A mismatched query neither reads nor replaces the valid cached proof.
        assertSame(
                facts.rootFacts(frameNative.body(), frameNative, analysis, frameNativeScope),
                facts.rootFacts(frameNative.body(), frameNative, analysis, frameNativeScope));

        Map<CanonicalCall, CanonicalClosure> valid =
                facts.bindingClosureCallCandidates(closure.body(), closure, analysis, scope);
        assertEquals(1, valid.size());
        assertTrue(
                facts.bindingClosureCallCandidates(frameNative.body(), closure, analysis, scope)
                        .isEmpty());
        assertTrue(
                facts.bindingClosureCallCandidates(
                                closure.body(), closure, analysis, analysis.topScope())
                        .isEmpty());
        assertTrue(
                facts.bindingClosureCallCandidates(closure.body(), closure, analysis, null)
                        .isEmpty());
        assertTrue(
                facts.bindingClosureCallCandidates(closure.body(), closure, null, scope)
                        .isEmpty());
        assertTrue(
                facts.bindingClosureCallCandidates(closure.body(), null, analysis, scope)
                        .isEmpty());
        assertSame(
                valid,
                facts.bindingClosureCallCandidates(closure.body(), closure, analysis, scope));
    }

    private void assertPersistent(String source) {
        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, canonicalize(source).expressions().get(0));
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeClosure(closure);
        CanonicalCompilationFacts.RootFacts rootFacts =
                new CanonicalCompilationFacts()
                        .rootFacts(
                                closure.body(),
                                closure,
                                analysis,
                                analysis.scopeOf(closure).orElseThrow());
        assertTrue(rootFacts.requiresPersistentFrameAuthority(), source);
    }

    private CanonicalSequence canonicalize(String source) {
        return (CanonicalSequence)
                canonicalizer.canonicalize(new ProtosParser(source).parseProgram());
    }
}
