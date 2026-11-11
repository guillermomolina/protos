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

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PLAT036 Candidate D, Slice 1 proofs: statically proven lexical binding
 * identity/presence-candidate metadata is discoverable from the canonical
 * tree, without altering which runtime path actually resolves any binding.
 */
class CanonicalBindingAnalyzerTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void sameNameBindingsInDifferentOwnersHaveDistinctIdentities() {
        CanonicalSequence program = canonicalizeProgram("x: 1\n(() => { x: 2\n x })");

        CanonicalCreate outerCreate = assertInstanceOf(CanonicalCreate.class, program.expressions().get(0));
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(1));
        CanonicalCreate innerCreate = assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        CanonicalBindingIdentity outerIdentity = analysis.identityOf(outerCreate).orElseThrow();
        CanonicalBindingIdentity innerIdentity = analysis.identityOf(innerCreate).orElseThrow();
        assertNotSame(outerIdentity, innerIdentity);
        assertEquals("x", outerIdentity.name());
        assertEquals("x", innerIdentity.name());
        assertNotSame(outerIdentity.owner(), innerIdentity.owner());
    }

    @Test
    void repeatedReferencesToSameBindingRetainSameIdentity() {
        CanonicalSequence program = canonicalizeProgram("x: 1\nx\nx");

        CanonicalCreate create = assertInstanceOf(CanonicalCreate.class, program.expressions().get(0));
        CanonicalLookup firstRead = assertInstanceOf(CanonicalLookup.class, program.expressions().get(1));
        CanonicalLookup secondRead = assertInstanceOf(CanonicalLookup.class, program.expressions().get(2));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        CanonicalBindingIdentity createIdentity = analysis.identityOf(create).orElseThrow();
        CanonicalBindingResolution.Resolved firstResolution =
                assertInstanceOf(
                        CanonicalBindingResolution.Resolved.class,
                        analysis.resolutionOf(firstRead).orElseThrow());
        CanonicalBindingResolution.Resolved secondResolution =
                assertInstanceOf(
                        CanonicalBindingResolution.Resolved.class,
                        analysis.resolutionOf(secondRead).orElseThrow());

        assertSame(createIdentity, firstResolution.identity());
        assertSame(createIdentity, secondResolution.identity());
    }

    @Test
    void currentVersusOuterClassificationRetainsStaticLexicalDepth() {
        CanonicalSequence program = canonicalizeProgram("x: 1\nx\n(() => x)");

        CanonicalLookup currentRead = assertInstanceOf(CanonicalLookup.class, program.expressions().get(1));
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(2));
        CanonicalLookup capturedRead =
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertInstanceOf(
                CanonicalBindingResolution.Resolved.class, analysis.resolutionOf(currentRead).orElseThrow());

        CanonicalBindingResolution.CapturedResolved capturedResolution =
                assertInstanceOf(
                        CanonicalBindingResolution.CapturedResolved.class,
                        analysis.resolutionOf(capturedRead).orElseThrow());
        assertEquals(1, capturedResolution.lexicalDepth());
    }

    @Test
    void capturedResolvedCannotNameDeclarationOwnedByCurrentScope() {
        CanonicalSequence program =
                canonicalizeProgram(
                        "outer: \"outer\"\n"
                                + "(() => { outer\n"
                                + " inner\n"
                                + " inner: \"inner\"\n"
                                + " inner })");

        CanonicalCreate outerCreate =
                assertInstanceOf(CanonicalCreate.class, program.expressions().get(0));
        CanonicalClosure closure =
                assertInstanceOf(CanonicalClosure.class, program.expressions().get(1));
        CanonicalLookup capturedOuter =
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(0));
        CanonicalLookup currentBeforeCreate =
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(1));
        CanonicalCreate currentCreate =
                assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(2));
        CanonicalLookup currentAfterCreate =
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(3));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalBindingIdentity outerIdentity = analysis.identityOf(outerCreate).orElseThrow();
        CanonicalBindingIdentity currentIdentity =
                analysis.identityOf(currentCreate).orElseThrow();

        CanonicalBindingResolution.CapturedResolved captured =
                assertInstanceOf(
                        CanonicalBindingResolution.CapturedResolved.class,
                        analysis.resolutionOf(capturedOuter).orElseThrow());
        assertSame(outerIdentity, captured.identity());
        assertEquals(1, captured.lexicalDepth());

        CanonicalBindingResolution.Candidate beforeCreation =
                assertInstanceOf(
                        CanonicalBindingResolution.Candidate.class,
                        analysis.resolutionOf(currentBeforeCreate).orElseThrow());
        assertSame(currentIdentity, beforeCreation.identity());
        assertEquals(0, beforeCreation.lexicalDepth());

        CanonicalBindingResolution.Resolved afterCreation =
                assertInstanceOf(
                        CanonicalBindingResolution.Resolved.class,
                        analysis.resolutionOf(currentAfterCreate).orElseThrow());
        assertSame(currentIdentity, afterCreation.identity());
    }

    @Test
    void parameterIdentityExistsBeforeSemanticPresence() {
        /* Non-rest parameters must precede defaulted parameters (grammar), so a forward
         * reference is exercised as a defaulted parameter's default referring to itself:
         * its identity must already exist (to be a valid reference target at all) while
         * establishment/PRESENT happens only once its own binding point is reached. */
        CanonicalSequence program = canonicalizeProgram("(a: a) => a");

        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalParameter parameterA = closure.parameters().get(0);
        CanonicalExpression defaultOfA = parameterA.defaultValue().orElseThrow();
        CanonicalLookup selfReferentialDefault = assertInstanceOf(CanonicalLookup.class, defaultOfA);

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        CanonicalBindingIdentity identityOfA = analysis.identityOf(parameterA).orElseThrow();

        /* a's identity already exists at the point its own default is analyzed, but it is
         * not yet established/PRESENT: same-scope, not-yet-present references classify
         * Candidate, never Resolved. */
        CanonicalBindingResolution.Candidate selfReference =
                assertInstanceOf(
                        CanonicalBindingResolution.Candidate.class,
                        analysis.resolutionOf(selfReferentialDefault).orElseThrow());
        assertSame(identityOfA, selfReference.identity());
        assertEquals(0, selfReference.lexicalDepth());
    }

    @Test
    void nearerScopeDeclaringLaterIsNotUnconditionallyResolvedBeforeItsOwnCreation() {
        CanonicalSequence program = canonicalizeProgram("x: \"outer\"\n(() => { x\n x: \"inner\"\n x })");

        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(1));
        CanonicalLookup readBeforeCreate =
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(0));
        CanonicalCreate innerCreate =
                assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(1));
        CanonicalLookup readAfterCreate =
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(2));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalBindingIdentity innerIdentity = analysis.identityOf(innerCreate).orElseThrow();

        /* Before its own scope's creation site: known as the nearest declaring scope's
         * candidate (not the outer x), but not unconditionally selectable yet. */
        CanonicalBindingResolution.Candidate beforeCreation =
                assertInstanceOf(
                        CanonicalBindingResolution.Candidate.class,
                        analysis.resolutionOf(readBeforeCreate).orElseThrow());
        assertSame(innerIdentity, beforeCreation.identity());
        assertEquals(0, beforeCreation.lexicalDepth());

        /* After its own scope's creation site: guaranteed PRESENT, resolved locally. */
        CanonicalBindingResolution.Resolved afterCreation =
                assertInstanceOf(
                        CanonicalBindingResolution.Resolved.class,
                        analysis.resolutionOf(readAfterCreate).orElseThrow());
        assertSame(innerIdentity, afterCreation.identity());
    }

    @Test
    void unresolvableNameRetainsExactDynamicClassification() {
        CanonicalSequence program = canonicalizeProgram("unknownName");

        CanonicalLookup lookup = assertInstanceOf(CanonicalLookup.class, program.expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertInstanceOf(CanonicalBindingResolution.Dynamic.class, analysis.resolutionOf(lookup).orElseThrow());
    }

    @Test
    void assignmentDestinationResolvesLikeAReadAtTheSameProgramPoint() {
        CanonicalSequence program = canonicalizeProgram("x: 1\nx = x + 1");

        CanonicalCreate create = assertInstanceOf(CanonicalCreate.class, program.expressions().get(0));
        CanonicalAssign assign = assertInstanceOf(CanonicalAssign.class, program.expressions().get(1));
        assertTrue(assign.target().isEmpty());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalBindingIdentity createIdentity = analysis.identityOf(create).orElseThrow();

        CanonicalBindingResolution.Resolved destination =
                assertInstanceOf(
                        CanonicalBindingResolution.Resolved.class,
                        analysis.resolutionOf(assign).orElseThrow());
        assertSame(createIdentity, destination.identity());
    }

    @Test
    void explicitMemberTargetsRemainOutsideTheLexicalBindingModel() {
        CanonicalSequence program = canonicalizeProgram("object.x: value");

        CanonicalCreate create = assertInstanceOf(CanonicalCreate.class, program.expressions().get(0));
        assertTrue(create.target().isPresent());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertTrue(analysis.identityOf(create).isEmpty());
    }

    @Test
    void bindingAnalysisWiringDoesNotChangeRuntimeLexicalResult() {
        com.guillermomolina.protos.runtime.ProtosObjectValue root =
                com.guillermomolina.protos.runtime.ProtosObjectValue.rootObject();
        com.guillermomolina.protos.runtime.ProtosActivation activation =
                com.guillermomolina.protos.runtime.ProtosTestPrelude.activation(
                        new com.guillermomolina.protos.runtime.ProtosObjectValue(root),
                        java.util.List.of(),
                        new com.guillermomolina.protos.runtime.ProtosObjectValue(root));

        /* Bare creation, bare re-assignment and a bare read, run through the real
         * lowering pipeline (CanonicalToBytecodeLowerer.lowerRoot now also computes
         * PLAT036 Slice 1 binding-identity metadata as a side effect). The exact
         * existing runtime lexical result must be unchanged. */
        Object result =
                ProtosTestExecutionSupport.evaluate(
                        "plat036-slice1-no-runtime-change.protos",
                        "x: \"one\"\nx = \"two\"\nx",
                        activation);

        assertEquals(
                "two",
                assertInstanceOf(
                                com.guillermomolina.protos.runtime.ProtosStringValue.class,
                                result)
                        .value());
    }

    @Test
    void i115ModuleDeclarationsAreNameStorageEvenWhenANestedClosureReadsThem() {
        CanonicalSequence program = canonicalizeProgram("x: 1\n(() => x)");
        CanonicalCreate create = assertInstanceOf(CanonicalCreate.class, program.expressions().get(0));
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(1));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        CanonicalBindingIdentity identity = analysis.identityOf(create).orElseThrow();
        assertEquals(CanonicalLexicalScope.Storage.NAME, CanonicalBindingAnalysis.storageOf(identity));
        assertEquals(List.of(), CanonicalBindingAnalysis.cellNamesOf(analysis.topScope()));
        assertEquals(
                List.of(),
                CanonicalBindingAnalysis.freeCellsOf(analysis.scopeOf(closure).orElseThrow()));
    }

    @Test
    void i115UncapturedClosureBindingsAreLocalStorage() {
        CanonicalSequence program = canonicalizeProgram("(n) => { a: n\n b: a\n b }");
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalLexicalScope scope = analysis.scopeOf(closure).orElseThrow();

        for (String name : List.of("n", "a", "b")) {
            assertEquals(CanonicalLexicalScope.Storage.LOCAL, scope.storageOf(name), name);
        }
        assertEquals(List.of(), CanonicalBindingAnalysis.cellNamesOf(scope));
        assertEquals(List.of(), CanonicalBindingAnalysis.freeCellsOf(scope));
    }

    @Test
    void i115ABindingReadByANestedClosureIsACellCarriedByThatClosure() {
        CanonicalSequence program = canonicalizeProgram("(() => { a: 1\n b: 2\n (() => a) })");
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalClosure inner =
                assertInstanceOf(CanonicalClosure.class, outer.body().expressions().get(2));
        CanonicalCreate createA =
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalLexicalScope outerScope = analysis.scopeOf(outer).orElseThrow();
        CanonicalBindingIdentity a = analysis.identityOf(createA).orElseThrow();

        assertEquals(CanonicalLexicalScope.Storage.CELL, CanonicalBindingAnalysis.storageOf(a));
        assertEquals(CanonicalLexicalScope.Storage.LOCAL, outerScope.storageOf("b"));
        assertEquals(List.of("a"), CanonicalBindingAnalysis.cellNamesOf(outerScope));
        assertEquals(
                List.of(a),
                CanonicalBindingAnalysis.freeCellsOf(analysis.scopeOf(inner).orElseThrow()));
    }

    @Test
    void i115ANestedCaptureIsCarriedByEveryCrossedClosure() {
        CanonicalSequence program = canonicalizeProgram("(() => { a: 1\n (() => (() => a)) })");
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalClosure middle =
                assertInstanceOf(CanonicalClosure.class, outer.body().expressions().get(1));
        CanonicalClosure inner =
                assertInstanceOf(CanonicalClosure.class, middle.body().expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalBindingIdentity a = analysis.scopeOf(outer).orElseThrow().identity("a");

        assertEquals(CanonicalLexicalScope.Storage.CELL, CanonicalBindingAnalysis.storageOf(a));
        assertEquals(List.of(a), CanonicalBindingAnalysis.freeCellsOf(analysis.scopeOf(middle).orElseThrow()));
        assertEquals(List.of(a), CanonicalBindingAnalysis.freeCellsOf(analysis.scopeOf(inner).orElseThrow()));
        assertEquals(0, analysis.scopeOf(inner).orElseThrow().freeCellIndex(a));
    }

    @Test
    void i115ACandidateOwnedByAnOuterClosureSelectsThatOwnersCell() {
        CanonicalSequence program = canonicalizeProgram("(() => { reader: (() => a)\n a: 1\n reader })");
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalCreate readerCreate =
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(0));
        CanonicalClosure reader = assertInstanceOf(CanonicalClosure.class, readerCreate.value());
        CanonicalLookup read =
                assertInstanceOf(CanonicalLookup.class, reader.body().expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalBindingResolution.Candidate candidate =
                assertInstanceOf(
                        CanonicalBindingResolution.Candidate.class,
                        analysis.resolutionOf(read).orElseThrow());

        assertSame(analysis.scopeOf(outer).orElseThrow(), candidate.identity().owner());
        assertEquals(
                CanonicalLexicalScope.Storage.CELL,
                CanonicalBindingAnalysis.storageOf(candidate.identity()));
        assertEquals(
                List.of(candidate.identity()),
                CanonicalBindingAnalysis.freeCellsOf(analysis.scopeOf(reader).orElseThrow()));
    }

    @Test
    void i115DynamicNamesCreateNoCells() {
        CanonicalSequence program = canonicalizeProgram("(() => unknownName)");
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalLookup read =
                assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertInstanceOf(
                CanonicalBindingResolution.Dynamic.class,
                analysis.resolutionOf(read).orElseThrow());
        assertEquals(
                List.of(),
                CanonicalBindingAnalysis.freeCellsOf(analysis.scopeOf(closure).orElseThrow()));
    }

    @Test
    void i115AnObjectBodyIsNotACaptureScopeAndItsBindingsAreMembers() {
        CanonicalSequence program = canonicalizeProgram("(() => { a: 1\n o: { b: a }\n o })");
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalCreate objectCreate =
                assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(1));
        CanonicalObject object = assertInstanceOf(CanonicalObject.class, objectCreate.value());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalLexicalScope closureScope = analysis.scopeOf(closure).orElseThrow();
        CanonicalLexicalScope objectScope = analysis.scopeOf(object).orElseThrow();

        assertEquals(CanonicalLexicalScope.Storage.LOCAL, closureScope.storageOf("a"));
        assertEquals(CanonicalLexicalScope.Storage.MEMBER, objectScope.storageOf("b"));
        assertEquals(List.of(), CanonicalBindingAnalysis.cellNamesOf(closureScope));
    }

    @Test
    void i115StorageClassificationIsStableAcrossClosureRematerialization() {
        String source = "(() => { a: 1\n (() => a) })";
        CanonicalSequence program = canonicalizeProgram(source);
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        CanonicalSequence reparsedProgram = canonicalizeProgram(source);
        CanonicalClosure reparsedOuter =
                assertInstanceOf(CanonicalClosure.class, reparsedProgram.expressions().get(0));
        CanonicalClosure reparsedInner =
                assertInstanceOf(CanonicalClosure.class, reparsedOuter.body().expressions().get(1));

        CanonicalBindingAnalysis outerRematerialized =
                analysis.rematerializeForReparsedClosure(reparsedOuter);
        CanonicalLexicalScope outerScope = outerRematerialized.scopeOf(reparsedOuter).orElseThrow();
        assertEquals(List.of("a"), CanonicalBindingAnalysis.cellNamesOf(outerScope));
        assertEquals(
                List.of(outerScope.identity("a")),
                CanonicalBindingAnalysis.freeCellsOf(
                        outerRematerialized.scopeOf(reparsedInner).orElseThrow()));

        CanonicalBindingAnalysis innerRematerialized =
                analysis.rematerializeForReparsedClosure(reparsedInner);
        CanonicalLexicalScope innerScope = innerRematerialized.scopeOf(reparsedInner).orElseThrow();
        List<CanonicalBindingIdentity> freeCells = CanonicalBindingAnalysis.freeCellsOf(innerScope);
        assertEquals(1, freeCells.size());
        assertEquals("a", freeCells.get(0).name());
        assertEquals(
                CanonicalLexicalScope.Storage.CELL,
                CanonicalBindingAnalysis.storageOf(freeCells.get(0)));
        CanonicalLookup read =
                assertInstanceOf(CanonicalLookup.class, reparsedInner.body().expressions().get(0));
        CanonicalBindingResolution.CapturedResolved captured =
                assertInstanceOf(
                        CanonicalBindingResolution.CapturedResolved.class,
                        innerRematerialized.resolutionOf(read).orElseThrow());
        assertSame(freeCells.get(0), captured.identity());
    }

    // ---- I115-S1c-L: identity, establishment and run-time access ------------

    @Test
    void s1cLCandidateBeforeEstablishmentIsPresenceCheckedAndOuterOwnerGetsAView() {
        CanonicalSequence program = canonicalizeProgram(
                "(() => { x: \"outer\"\n inner: (() => { early: x\n x: \"inner\"\n x })\n inner })");
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalCreate innerCreate =
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(1));
        CanonicalClosure inner = assertInstanceOf(CanonicalClosure.class, innerCreate.value());
        CanonicalCreate early = assertInstanceOf(CanonicalCreate.class, inner.body().expressions().get(0));
        CanonicalLookup before = assertInstanceOf(CanonicalLookup.class, early.value());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalLexicalScope innerScope = analysis.scopeOf(inner).orElseThrow();
        CanonicalLexicalScope outerScope = analysis.scopeOf(outer).orElseThrow();

        CanonicalBindingResolution.Candidate candidate =
                assertInstanceOf(
                        CanonicalBindingResolution.Candidate.class,
                        analysis.resolutionOf(before).orElseThrow());
        assertSame(innerScope, candidate.identity().owner());
        assertFalse(candidate.establishedAtSite());
        assertEquals(
                CanonicalBindingAnalysis.LexicalAccess.PRESENCE_CHECKED,
                analysis.accessOf(before));
        // The outer, already PRESENT x must stay selectable by name.
        assertTrue(outerScope.requiresLexicalView());
        assertFalse(innerScope.requiresLexicalView());
    }

    @Test
    void s1cLSameSiteAfterEstablishmentIsDirect() {
        CanonicalSequence program = canonicalizeProgram(
                "(() => { x: \"outer\"\n inner: (() => { early: x\n x: \"inner\"\n x })\n inner })");
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalCreate innerCreate =
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(1));
        CanonicalClosure inner = assertInstanceOf(CanonicalClosure.class, innerCreate.value());
        CanonicalLookup after = assertInstanceOf(CanonicalLookup.class, inner.body().expressions().get(2));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        CanonicalBindingResolution resolution = analysis.resolutionOf(after).orElseThrow();
        assertInstanceOf(CanonicalBindingResolution.Resolved.class, resolution);
        assertTrue(resolution.establishedAtSite());
        assertEquals(CanonicalBindingAnalysis.LexicalAccess.DIRECT, analysis.accessOf(after));
    }

    @Test
    void s1cLCapturedCandidateBeforeAndAfterOwnerEstablishment() {
        CanonicalSequence program = canonicalizeProgram(
                "(() => { early: (() => a)\n a: 1\n late: (() => a)\n early })");
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalClosure earlyReader = assertInstanceOf(CanonicalClosure.class,
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(0)).value());
        CanonicalClosure lateReader = assertInstanceOf(CanonicalClosure.class,
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(2)).value());
        CanonicalLookup earlyRead =
                assertInstanceOf(CanonicalLookup.class, earlyReader.body().expressions().get(0));
        CanonicalLookup lateRead =
                assertInstanceOf(CanonicalLookup.class, lateReader.body().expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalLexicalScope outerScope = analysis.scopeOf(outer).orElseThrow();

        CanonicalBindingResolution.Candidate candidate =
                assertInstanceOf(
                        CanonicalBindingResolution.Candidate.class,
                        analysis.resolutionOf(earlyRead).orElseThrow());
        CanonicalBindingResolution.CapturedResolved captured =
                assertInstanceOf(
                        CanonicalBindingResolution.CapturedResolved.class,
                        analysis.resolutionOf(lateRead).orElseThrow());
        // One owner identity and one cell for both sites; the cell exists
        // before its value is PRESENT.
        assertSame(candidate.identity(), captured.identity());
        assertSame(outerScope, candidate.identity().owner());
        assertEquals(CanonicalLexicalScope.Storage.CELL,
                CanonicalBindingAnalysis.storageOf(candidate.identity()));
        assertEquals(
                CanonicalBindingAnalysis.LexicalAccess.PRESENCE_CHECKED,
                analysis.accessOf(earlyRead));
        assertEquals(CanonicalBindingAnalysis.LexicalAccess.DIRECT, analysis.accessOf(lateRead));
    }

    @Test
    void s1cLAssignmentDestinationIsClassifiedBeforeItsRightHandSide() {
        CanonicalSequence program = canonicalizeProgram(
                "(() => { x: 1\n f: (() => { x = 3\n x: 2 })\n f })");
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalClosure writer = assertInstanceOf(CanonicalClosure.class,
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(1)).value());
        CanonicalAssign assign =
                assertInstanceOf(CanonicalAssign.class, writer.body().expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        // The writer creates its own x only later: the destination is the
        // writer's not-yet-established candidate, classified at the
        // assignment, before its right-hand side.
        CanonicalBindingResolution.Candidate destination =
                assertInstanceOf(
                        CanonicalBindingResolution.Candidate.class,
                        analysis.resolutionOf(assign).orElseThrow());
        assertSame(analysis.scopeOf(writer).orElseThrow(), destination.identity().owner());
        assertEquals(
                CanonicalBindingAnalysis.LexicalAccess.PRESENCE_CHECKED,
                analysis.accessOf(assign));
    }

    @Test
    void s1cLObjectBodyMembersNeverBecomeCapturedLexicalBindings() {
        CanonicalSequence program = canonicalizeProgram(
                "(() => { holder: { m: 1\n reader: (() => m) }\n holder })");
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalObject object = assertInstanceOf(CanonicalObject.class,
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(0)).value());
        CanonicalClosure reader = assertInstanceOf(CanonicalClosure.class,
                assertInstanceOf(CanonicalCreate.class, object.body().expressions().get(1)).value());
        CanonicalLookup read = assertInstanceOf(CanonicalLookup.class, reader.body().expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertInstanceOf(CanonicalBindingResolution.Dynamic.class, analysis.resolutionOf(read).orElseThrow());
        assertEquals(CanonicalBindingAnalysis.LexicalAccess.SELECTED, analysis.accessOf(read));
        assertEquals(List.of(), CanonicalBindingAnalysis.freeCellsOf(analysis.scopeOf(reader).orElseThrow()));
        assertEquals(List.of(), CanonicalBindingAnalysis.cellNamesOf(analysis.scopeOf(outer).orElseThrow()));
        assertFalse(analysis.scopeOf(outer).orElseThrow().requiresLexicalView());
    }

    @Test
    void s1cLParameterDefaultsAndNestedClosuresKeepDistinctScopeIdentities() {
        CanonicalSequence program = canonicalizeProgram(
                "((a, b: a) => { inner: ((a) => a + b)\n inner })");
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalParameter outerA = outer.parameters().get(0);
        CanonicalLookup defaultRead =
                assertInstanceOf(CanonicalLookup.class, outer.parameters().get(1).defaultValue().orElseThrow());
        CanonicalClosure inner = assertInstanceOf(CanonicalClosure.class,
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(0)).value());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalBindingIdentity outerAIdentity = analysis.identityOf(outerA).orElseThrow();
        CanonicalBindingIdentity innerAIdentity = analysis.identityOf(inner.parameters().get(0)).orElseThrow();

        // The default sees the already established earlier parameter.
        CanonicalBindingResolution.Resolved defaultResolution =
                assertInstanceOf(
                        CanonicalBindingResolution.Resolved.class,
                        analysis.resolutionOf(defaultRead).orElseThrow());
        assertSame(outerAIdentity, defaultResolution.identity());
        assertEquals(CanonicalBindingAnalysis.LexicalAccess.DIRECT, analysis.accessOf(defaultRead));
        assertNotSame(outerAIdentity, innerAIdentity);
        assertNotSame(analysis.scopeOf(outer).orElseThrow(), analysis.scopeOf(inner).orElseThrow());
    }

    @Test
    void s1cLObservedContextMakesOwnedBindingsPresenceCheckedAndCreationsChecked() {
        CanonicalSequence program = canonicalizeProgram(
                "(() => { x: 1\n y: x\n saved: context\n x })");
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalCreate createX = assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(0));
        CanonicalLookup lastRead = assertInstanceOf(CanonicalLookup.class, closure.body().expressions().get(3));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertTrue(analysis.scopeOf(closure).orElseThrow().observesContext());
        assertTrue(analysis.requiresPresenceCheckedCreation(createX));
        assertEquals(CanonicalBindingAnalysis.LexicalAccess.PRESENCE_CHECKED, analysis.accessOf(lastRead));
    }

    @Test
    void s1cLScopeBetweenSiteAndOwnerThatObservesItsContextSelectsTheBinding() {
        CanonicalSequence program = canonicalizeProgram(
                "(() => { x: 1\n middle: (() => { saved: context\n (() => x) })\n middle })");
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalClosure middle = assertInstanceOf(CanonicalClosure.class,
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(1)).value());
        CanonicalClosure reader = assertInstanceOf(CanonicalClosure.class, middle.body().expressions().get(1));
        CanonicalLookup read = assertInstanceOf(CanonicalLookup.class, reader.body().expressions().get(0));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertInstanceOf(CanonicalBindingResolution.CapturedResolved.class, analysis.resolutionOf(read).orElseThrow());
        assertEquals(CanonicalBindingAnalysis.LexicalAccess.SELECTED, analysis.accessOf(read));
        assertTrue(analysis.scopeOf(outer).orElseThrow().requiresLexicalView());
    }

    @Test
    void s1cLStaticDuplicateCreationIsPresenceChecked() {
        CanonicalSequence program = canonicalizeProgram("(() => { x: 1\n x: 2 })");
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalCreate first = assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(0));
        CanonicalCreate second = assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(1));

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertFalse(analysis.requiresPresenceCheckedCreation(first));
        assertTrue(analysis.requiresPresenceCheckedCreation(second));
    }

    @Test
    void s1cLRematerializedCapturedSiteKeepsPresenceCheckOfARemovableBinding() {
        // The owner observes its Context, so the captured binding may be
        // removed and recreated at run time: the rematerialized site must
        // keep validating membership, never become a DIRECT read.
        String source = "(() => { a: 1\n reader: (() => a)\n saved: context\n reader })";
        CanonicalSequence program = canonicalizeProgram(source);
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalClosure outer = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalClosure reader = assertInstanceOf(CanonicalClosure.class,
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(1)).value());
        CanonicalLookup originalRead =
                assertInstanceOf(CanonicalLookup.class, reader.body().expressions().get(0));
        assertEquals(
                CanonicalBindingAnalysis.LexicalAccess.PRESENCE_CHECKED,
                analysis.accessOf(originalRead));

        CanonicalSequence reparsedProgram = canonicalizeProgram(source);
        CanonicalClosure reparsedOuter =
                assertInstanceOf(CanonicalClosure.class, reparsedProgram.expressions().get(0));
        CanonicalClosure reparsedReader = assertInstanceOf(CanonicalClosure.class,
                assertInstanceOf(CanonicalCreate.class, reparsedOuter.body().expressions().get(1)).value());
        CanonicalLookup reparsedRead =
                assertInstanceOf(CanonicalLookup.class, reparsedReader.body().expressions().get(0));

        CanonicalBindingAnalysis rematerialized = analysis.rematerializeForReparsedClosure(reparsedReader);

        CanonicalBindingResolution.CapturedResolved captured =
                assertInstanceOf(
                        CanonicalBindingResolution.CapturedResolved.class,
                        rematerialized.resolutionOf(reparsedRead).orElseThrow());
        assertTrue(captured.identity().owner().observesContext());
        assertEquals(
                CanonicalBindingAnalysis.LexicalAccess.PRESENCE_CHECKED,
                rematerialized.accessOf(reparsedRead));
    }

    @Test
    void s1cLRematerializedCapturedSiteBehindAnObservedScopeStaysSelected() {
        String source = "(() => { x: 1\n middle: (() => { saved: context\n (() => x) })\n middle })";
        CanonicalSequence program = canonicalizeProgram(source);
        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        CanonicalSequence reparsedProgram = canonicalizeProgram(source);
        CanonicalClosure reparsedOuter =
                assertInstanceOf(CanonicalClosure.class, reparsedProgram.expressions().get(0));
        CanonicalClosure reparsedMiddle = assertInstanceOf(CanonicalClosure.class,
                assertInstanceOf(CanonicalCreate.class, reparsedOuter.body().expressions().get(1)).value());
        CanonicalClosure reparsedReader =
                assertInstanceOf(CanonicalClosure.class, reparsedMiddle.body().expressions().get(1));
        CanonicalLookup reparsedRead =
                assertInstanceOf(CanonicalLookup.class, reparsedReader.body().expressions().get(0));

        CanonicalBindingAnalysis rematerialized = analysis.rematerializeForReparsedClosure(reparsedMiddle);

        assertEquals(
                CanonicalBindingAnalysis.LexicalAccess.SELECTED,
                rematerialized.accessOf(reparsedRead));
    }

    @Test
    void s1cLAbsentSameNameMemberLetsTheObjectBodyReachTheEnclosingParameter() {
        CanonicalSequence program = canonicalizeProgram("((name) => { d: { name: name }\n d })");
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalObject object = assertInstanceOf(CanonicalObject.class,
                assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(0)).value());
        CanonicalLookup read = assertInstanceOf(CanonicalLookup.class,
                assertInstanceOf(CanonicalCreate.class, object.body().expressions().get(0)).value());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        CanonicalBindingResolution.Candidate candidate = assertInstanceOf(
                CanonicalBindingResolution.Candidate.class, analysis.resolutionOf(read).orElseThrow());
        assertSame(analysis.scopeOf(object).orElseThrow(), candidate.identity().owner());
        assertEquals(CanonicalBindingAnalysis.LexicalAccess.PRESENCE_CHECKED, analysis.accessOf(read));
        assertTrue(analysis.scopeOf(closure).orElseThrow().requiresLexicalView());
        assertTrue(analysis.scopeOf(object).orElseThrow().requiresEnclosingFrameView());
    }

    @Test
    void s1cLDifferentlyNamedMemberKeepsTheDirectEnclosingRead() {
        CanonicalSequence program = canonicalizeProgram("((name) => { d: { label: name }\n d })");
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalObject object = assertInstanceOf(CanonicalObject.class,
                assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(0)).value());
        CanonicalLookup read = assertInstanceOf(CanonicalLookup.class,
                assertInstanceOf(CanonicalCreate.class, object.body().expressions().get(0)).value());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertEquals(CanonicalBindingAnalysis.LexicalAccess.DIRECT, analysis.accessOf(read));
        assertFalse(analysis.scopeOf(closure).orElseThrow().requiresLexicalView());
        assertFalse(analysis.scopeOf(object).orElseThrow().requiresEnclosingFrameView());
    }

    @Test
    void s1cLReadAfterMemberEstablishmentResolvesTheMember() {
        CanonicalSequence program = canonicalizeProgram("((name) => { d: { name: 1\n copy: name }\n d })");
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalObject object = assertInstanceOf(CanonicalObject.class,
                assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(0)).value());
        CanonicalLookup read = assertInstanceOf(CanonicalLookup.class,
                assertInstanceOf(CanonicalCreate.class, object.body().expressions().get(1)).value());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        CanonicalBindingResolution.Resolved resolved = assertInstanceOf(
                CanonicalBindingResolution.Resolved.class, analysis.resolutionOf(read).orElseThrow());
        assertSame(analysis.scopeOf(object).orElseThrow(), resolved.identity().owner());
        assertFalse(analysis.scopeOf(object).orElseThrow().requiresEnclosingFrameView());
    }

    @Test
    void s1cLNestedObjectBodiesAllRecordTheEnclosingFrameView() {
        CanonicalSequence program =
                canonicalizeProgram("((name) => { outer: { inner: { name: name }\n tag: 1 }\n outer })");
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalObject outer = assertInstanceOf(CanonicalObject.class,
                assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(0)).value());
        CanonicalObject inner = assertInstanceOf(CanonicalObject.class,
                assertInstanceOf(CanonicalCreate.class, outer.body().expressions().get(0)).value());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertTrue(analysis.scopeOf(closure).orElseThrow().requiresLexicalView());
        assertTrue(analysis.scopeOf(inner).orElseThrow().requiresEnclosingFrameView());
        assertTrue(analysis.scopeOf(outer).orElseThrow().requiresEnclosingFrameView());
    }

    @Test
    void s1cLAbsentMemberWithoutEnclosingBindingPublishesNothing() {
        CanonicalSequence program = canonicalizeProgram("(() => { d: { name: name }\n d })");
        CanonicalClosure closure = assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalObject object = assertInstanceOf(CanonicalObject.class,
                assertInstanceOf(CanonicalCreate.class, closure.body().expressions().get(0)).value());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertFalse(analysis.scopeOf(closure).orElseThrow().requiresLexicalView());
        assertFalse(analysis.scopeOf(object).orElseThrow().requiresEnclosingFrameView());
    }

    @Test
    void s1cLModuleLevelObjectBodyNeverRecordsAClosureFrameView() {
        CanonicalSequence program = canonicalizeProgram("name: 1\nd: { name: name }");
        CanonicalObject object = assertInstanceOf(CanonicalObject.class,
                assertInstanceOf(CanonicalCreate.class, program.expressions().get(1)).value());

        CanonicalBindingAnalysis analysis = CanonicalBindingAnalyzer.analyzeModule(program);

        assertFalse(analysis.scopeOf(object).orElseThrow().requiresEnclosingFrameView());
    }


    @Test
    void contextualWhileHasIndependentClosureScopesAndRealCaptures() {
        CanonicalSequence program = canonicalizeProgram(
                "(() => { i: 0\n while(i < 2) { item: i\n i = i + 1 }\n i })");

        CanonicalClosure outer =
                assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalSend loop =
                assertInstanceOf(CanonicalSend.class, outer.body().expressions().get(1));
        CanonicalClosure condition =
                assertInstanceOf(CanonicalClosure.class, loop.receiver());
        CanonicalClosure body =
                assertInstanceOf(CanonicalClosure.class, loop.arguments().get(0));
        CanonicalCreate item =
                assertInstanceOf(CanonicalCreate.class, body.body().expressions().get(0));
        CanonicalLookup capturedI =
                assertInstanceOf(CanonicalLookup.class, item.value());

        CanonicalBindingAnalysis analysis =
                CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalLexicalScope outerScope = analysis.scopeOf(outer).orElseThrow();
        CanonicalLexicalScope bodyScope = analysis.scopeOf(body).orElseThrow();

        assertTrue(loop.contextual());
        assertTrue(analysis.scopeOf(condition).isPresent());
        assertEquals(CanonicalLexicalScope.Storage.CELL, outerScope.storageOf("i"));
        assertEquals(CanonicalLexicalScope.Storage.LOCAL, bodyScope.storageOf("item"));
        assertTrue(!outerScope.declaresName("item"));
        CanonicalBindingResolution.CapturedResolved captured =
                assertInstanceOf(
                        CanonicalBindingResolution.CapturedResolved.class,
                        analysis.resolutionOf(capturedI).orElseThrow());
        assertSame(outerScope.identity("i"), captured.identity());
    }

    @Test
    void contextualIfCapturesParentWithoutMovingItsDeclarations() {
        CanonicalSequence program = canonicalizeProgram(
                "(() => { value: 1\n if(true) { inside: value\n inside }\n value })");

        CanonicalClosure outer =
                assertInstanceOf(CanonicalClosure.class, program.expressions().get(0));
        CanonicalSend conditional =
                assertInstanceOf(CanonicalSend.class, outer.body().expressions().get(1));
        CanonicalClosure branch =
                assertInstanceOf(CanonicalClosure.class, conditional.arguments().get(0));
        CanonicalCreate inside =
                assertInstanceOf(CanonicalCreate.class, branch.body().expressions().get(0));

        CanonicalBindingAnalysis analysis =
                CanonicalBindingAnalyzer.analyzeModule(program);
        CanonicalLexicalScope outerScope = analysis.scopeOf(outer).orElseThrow();
        CanonicalLexicalScope branchScope = analysis.scopeOf(branch).orElseThrow();

        assertTrue(conditional.contextual());
        assertTrue(!outerScope.declaresName("inside"));
        assertEquals(CanonicalLexicalScope.Storage.CELL, outerScope.storageOf("value"));
        assertEquals(CanonicalLexicalScope.Storage.LOCAL, branchScope.storageOf("inside"));
        CanonicalLookup read =
                assertInstanceOf(CanonicalLookup.class, inside.value());
        CanonicalBindingResolution.CapturedResolved captured =
                assertInstanceOf(
                        CanonicalBindingResolution.CapturedResolved.class,
                        analysis.resolutionOf(read).orElseThrow());
        assertSame(outerScope.identity("value"), captured.identity());
    }

    private CanonicalSequence canonicalizeProgram(String source) {
        return (CanonicalSequence)
                canonicalizer.canonicalize(new ProtosParser(source).parseProgram());
    }
}
