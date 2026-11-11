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
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.source.Source;
import java.util.List;
import java.util.Set;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I115 focal evidence for the single lexical storage model chosen at
 * lowering time: module (ROOT) declarations are NAME bindings of the module
 * context, ordinary Closure bindings are Bytecode DSL locals, bindings
 * captured by a nested Closure are shared cells, dynamic names are one name
 * operation, and explicit targets keep member operations. Every assertion
 * inspects the emitted instructions as well as the value.
 */
final class ProtosI115LexicalStorageConvergenceTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    /** Storage-selection operations retired by I115; never emitted for any binding. */
    private static final Set<String> RETIRED_SELECTION =
            Set.of(
                    "IsCompactLocalFrame",
                    "ReadRootFrameLocal",
                    "ResolveRootFrameLocalWriteTarget",
                    "AssignRootFrameLocal",
                    "CreateCurrentFrameLocal",
                    "InstallFrameLexicalAuthority",
                    "BindUnaryFrameParameter",
                    "BindRequiredFrameParameter",
                    "BindClosureFrameParameter",
                    "BindClosureIndexedParameter",
                    "ReadFrameLocal",
                    "ReadCapturedFrameLocalAtRoot",
                    "EnsureLiveFrameLexicalAuthority",
                    "PromoteFrameLexicalAuthority",
                    // I115-S1c-L: no legacy frame-authority or captured-access
                    // operation and no uninitialized-binding shortcut reappears.
                    "UninitializedBinding",
                    "ReadCapturedFrameLocal",
                    "ResolveCapturedWritableLexicalTarget",
                    "SelectCapturedMaterializedOwnerFrame",
                    "ResolveCapturedMaterializedWritableLexicalTarget",
                    "ReadInlineCapturedFrameLocal");

    private static final Set<String> CELL_OPERATIONS =
            Set.of(
                    "CreateCell",
                    "LoadCell",
                    "StoreCell",
                    "LoadCapturedCell",
                    "CaptureClosureCells",
                    "LoadLexicalCell",
                    "SelectLexicalCellWrite",
                    "AssignLexicalCell",
                    "CreateLexicalCell");

    /* I115-S1c-L operations that bind or project an escaped frame. */
    private static final Set<String> FRAME_PUBLICATION =
            Set.of("EnsureCurrentClosureLexicalView", "PublishCurrentClosureContext");

    /* The first invocation precedes x's establishment in the captured module. */
    private static final String MODULE_CANDIDATE_SOURCE =
            "reader: (() => x)\n"
                    + "receiver: { x: {}\n read: reader }\n"
                    + "before: receiver.read()\n"
                    + "x: {}\n"
                    + "reader";

    @Test
    void moduleRootCreatesItsBindingsInTheModuleContext() throws Exception {
        withFixture(
                fixture -> {
                    ProtosBytecodeRootNode root = lower(fixture.language, "x: 1\ny: x\ny");
                    List<String> names = operations(root.getBytecodeNode());

                    assertEquals(1L, root.getCallTarget().call(fixture.module));
                    assertEquals(1L, slot(fixture.module, "x"));
                    assertEquals(1L, slot(fixture.module, "y"));
                    assertTrue(names.contains("CreateCurrentLocalSlot"), names::toString);
                    assertNoRetiredSelection(names);
                    assertNoCellOperation(names);
                });
    }

    @Test
    void moduleRootCandidateFallsBackToPreludeBeforeEstablishment() throws Exception {
        withFixture(
                fixture -> {
                    ProtosBytecodeRootNode root =
                            lower(
                                    fixture.language,
                                    "before: Context\nContext: {}\nafter: Context\nbefore");
                    List<String> names = operations(root.getBytecodeNode());
                    ProtosObjectValue preludeContext =
                            fixture.module.prelude().orElseThrow().contextPrototype();

                    assertTrue(names.contains("ReadGlobal"), names::toString);
                    assertSame(preludeContext, root.getCallTarget().call(fixture.module));
                    assertSame(preludeContext, slot(fixture.module, "before"));
                    Object moduleContext = slot(fixture.module, "Context");
                    assertNotSame(preludeContext, moduleContext);
                    assertSame(moduleContext, slot(fixture.module, "after"));
                });
    }

    @Test
    void sourceBackedModuleBindingsArePublishedByTheGeneralCreation() throws Exception {
        withFixture(
                fixture -> {
                    Object result =
                            run(
                                    fixture,
                                    "ifNotNull: ((value) => value)\n"
                                            + "ifNull: ((value) => value)\n"
                                            + "ifNotNull");

                    assertSame(slot(fixture.module, "ifNotNull"), result);
                    assertInstanceOf(ProtosClosureValue.class, slot(fixture.module, "ifNull"));
                });
    }

    @Test
    void twoModulesDoNotShareMutableBindings() throws Exception {
        withFixture(
                fixture -> {
                    ProtosActivation other =
                            fixture.module.prelude().orElseThrow().newModuleActivation();
                    lower(fixture.language, "x: 1").getCallTarget().call(fixture.module);
                    lower(fixture.language, "x: 2").getCallTarget().call(other);

                    assertEquals(1L, slot(fixture.module, "x"));
                    assertEquals(2L, slot(other, "x"));
                    assertNotSame(fixture.module.context(), other.context());
                });
    }

    @Test
    void closureWithTwoLocalsUsesOnlyNativeLocals() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue closure = closure(fixture, "(() => { a: 1\n b: a\n b })");
                    List<String> names = operations(activationNode(closure));

                    assertTrue(names.stream().anyMatch(name -> name.startsWith("store.local")), names::toString);
                    assertTrue(names.stream().anyMatch(name -> name.startsWith("load.local")), names::toString);
                    assertFalse(names.contains("Lookup"), names::toString);
                    assertFalse(names.contains("CurrentActivation"), names::toString);
                    assertNoRetiredSelection(names);
                    assertNoCellOperation(names);
                    assertEquals(1L, execute(fixture, closure));
                });
    }

    @Test
    void unaryParameterIsStoredFromTheCallAbiIntoItsLocal() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue closure = closure(fixture, "((n) => n)");
                    List<String> names = operations(activationNode(closure));

                    assertTrue(names.contains("CopySuppliedArguments"), names::toString);
                    assertTrue(names.stream().anyMatch(name -> name.startsWith("load.local")), names::toString);
                    assertNoRetiredSelection(names);
                    assertNoCellOperation(names);
                    assertEquals(7L, execute(fixture, closure, 7L));
                });
    }

    @Test
    void multipleDefaultAndRestParametersUseTheirClassifiedStorage() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue closure = closure(fixture, "((a, b: a, ...rest) => b)");
                    List<String> names = operations(activationNode(closure));

                    assertTrue(names.contains("LoadSuppliedArgument"), names::toString);
                    assertTrue(names.contains("HasSuppliedArgument"), names::toString);
                    assertTrue(names.contains("LoadRestArguments"), names::toString);
                    assertNoRetiredSelection(names);
                    assertNoCellOperation(names);
                    // The default evaluates after a is bound.
                    assertEquals(3L, execute(fixture, closure, 3L));
                    assertEquals(4L, execute(fixture, closure, 3L, 4L));
                });
    }

    @Test
    void longAndDoubleValuesKeepTheirRepresentationThroughLocals() throws Exception {
        withFixture(
                fixture -> {
                    assertInstanceOf(
                            Long.class,
                            execute(fixture, closure(fixture, "(() => { i: 1\n j: i\n j })")));
                    assertInstanceOf(
                            Double.class,
                            execute(fixture, closure(fixture, "(() => { d: 2.5\n e: d\n e })")));
                });
    }

    @Test
    void immediateLiteralCallKeepsDirectInlineStorage()
            throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue outer =
                            closure(fixture, """
                                    (() => {
                                        value: 41
                                        (() => value + 1)()
                                    })
                                    """);

                    List<String> names =
                            operations(activationNode(outer));

                    assertFalse(names.contains("CreateCell"), names::toString);
                    assertFalse(names.contains("StoreCell"), names::toString);
                    assertFalse(names.contains("LoadCell"), names::toString);
                    assertFalse(names.contains("CaptureClosureCells"), names::toString);
                    assertTrue(
                            names.stream().anyMatch(
                                    name -> name.startsWith("load.local")),
                            names::toString);
                    assertNoRetiredSelection(names);
                    assertEquals(42L, execute(fixture, outer));
                });
    }

    @Test
    void capturedBindingIsOneCellSharedByReference() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue closure =
                            closure(
                                    fixture,
                                    "(() => { a: 1\n"
                                            + " set: ((v) => a = v)\n"
                                            + " get: (() => a)\n"
                                            + " set(5)\n"
                                            + " get() })");
                    List<String> names = operations(activationNode(closure));

                    assertTrue(names.contains("CreateCell"), names::toString);
                    assertTrue(names.contains("StoreCell"), names::toString);
                    assertTrue(names.contains("CaptureClosureCells"), names::toString);
                    assertNoRetiredSelection(names);
                    assertEquals(5L, execute(fixture, closure));
                });
    }

    @Test
    void twoClosuresCapturingOneBindingShareTheSameCell() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue object =
                            assertInstanceOf(
                                    ProtosObjectValue.class,
                                    execute(
                                            fixture,
                                            closure(
                                                    fixture,
                                                    "(() => { a: 1\n"
                                                            + " o: { first: (() => a)\n"
                                                            + " second: (() => a) }\n"
                                                            + " o })")));
                    ProtosClosureValue first =
                            assertInstanceOf(
                                    ProtosClosureValue.class,
                                    object.readLocalSlot("first").orElseThrow());
                    ProtosClosureValue second =
                            assertInstanceOf(
                                    ProtosClosureValue.class,
                                    object.readLocalSlot("second").orElseThrow());

                    ProtosLexicalCell cell = first.capturedCellsForRuntime()[0];
                    assertSame(cell, second.capturedCellsForRuntime()[0]);
                    assertEquals(1L, cell.valueOrNullForRuntime());

                    List<String> reader = operations(activationNode(first));
                    assertTrue(reader.contains("LoadCapturedCell"), reader::toString);
                    assertTrue(reader.contains("LoadCell"), reader::toString);
                    assertNoRetiredSelection(reader);
                });
    }

    @Test
    void moduleCaptureReadsTheModuleContextNotAPrivateLocal() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue closure = closure(fixture, "x: 1\n(() => x)");
                    List<String> names = operations(activationNode(closure));

                    assertTrue(names.contains("ReadGlobal"), names::toString);
                    assertNoCellOperation(names);
                    assertNoRetiredSelection(names);

                    fixture.module.context().assignLocalSlot("x", 9L);
                    assertEquals(9L, execute(fixture, closure));
                });
    }

    @Test
    void absentCapturedModuleBindingFallsBackToExactReceiverValue() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue reader = closure(fixture, MODULE_CANDIDATE_SOURCE);
                    List<String> names = operations(activationNode(reader));
                    ProtosObjectValue receiver =
                            assertInstanceOf(ProtosObjectValue.class, slot(fixture.module, "receiver"));

                    assertTrue(names.contains("ReadGlobal"), names::toString);
                    assertSame(receiver.readLocalSlot("x").orElseThrow(), slot(fixture.module, "before"));
                });
    }

    @Test
    void establishedCapturedModuleBindingTakesPrecedenceOverReceiver() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue reader = closure(fixture, MODULE_CANDIDATE_SOURCE);
                    List<String> names = operations(activationNode(reader));
                    Object moduleValue = slot(fixture.module, "x");

                    assertTrue(names.contains("ReadGlobal"), names::toString);
                    assertNotSame(slot(fixture.module, "before"), moduleValue);
                    assertSame(moduleValue, run(fixture, "receiver.read()"));
                });
    }

    @Test
    void absentCapturedModuleBindingWithoutFallbackSignalsSlotNotFound() throws Exception {
        withFixture(
                fixture -> {
                    ProtosSignalException failure =
                            assertThrows(
                                    ProtosSignalException.class,
                                    () -> run(fixture, "reader: (() => x)\nreader()\nx: {}"));
                    ProtosClosureValue reader =
                            assertInstanceOf(ProtosClosureValue.class, slot(fixture.module, "reader"));
                    List<String> names = operations(activationNode(reader));

                    assertTrue(names.contains("ReadGlobal"), names::toString);
                    assertSame(
                            ProtosCoreErrors.prototype(
                                    fixture.module, ProtosCoreErrors.StandardError.SLOT_NOT_FOUND),
                            failure.error().parent().orElseThrow());
                });
    }

    @Test
    void dynamicNameIsOneNameOperation() throws Exception {
        withFixture(
                fixture -> {
                    List<String> names =
                            operations(activationNode(closure(fixture, "(() => unknownName)")));

                    assertEquals(1, names.stream().filter("Lookup"::equals).count(), names::toString);
                    assertNoCellOperation(names);
                    assertNoRetiredSelection(names);
                });
    }

    @Test
    void explicitTargetCreationRemainsAMemberOperation() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue closure =
                            closure(fixture, "(() => { o: { y: 0 }\n o.x: 1\n o })");
                    List<String> names = operations(activationNode(closure));

                    assertTrue(names.contains("CreateAttribute"), names::toString);
                    ProtosObjectValue object =
                            assertInstanceOf(ProtosObjectValue.class, execute(fixture, closure));
                    assertEquals(1L, object.readLocalSlot("x").orElseThrow());
                });
    }

    @Test
    void closureWithoutCapturesCreatesNoCellAndMaterializesNoContext() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue closure =
                            closure(fixture, "(() => { a: 1\n f: (() => 2)\n a })");
                    List<String> names = operations(activationNode(closure));

                    assertNoCellOperation(names);
                    assertFalse(names.contains("CurrentActivation"), names::toString);
                    assertEquals(0, closure.capturedCellsForRuntime().length);
                    assertEquals(1L, execute(fixture, closure));
                });
    }

    @Test
    void observedContextHoldsEstablishedLocalBindings() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue observed =
                            assertInstanceOf(
                                    ProtosObjectValue.class,
                                    execute(fixture, closure(fixture, "(() => { x: 1\n context })")));

                    /*
                     * I115-S1c-L: the observed Context is the live frame-backed
                     * view of the Closure activation, so its established LOCAL
                     * binding is a binding of the Context (formerly recorded as
                     * a KNOWN I115 SEMANTIC DIVERGENCE).
                     */
                    assertTrue(observed.hasLocalSlot("x"));
                    assertEquals(1L, observed.readLocalSlot("x").orElseThrow());
                });
    }

    // ---- I115-S1c-L: live frame projection and physical access -------------

    @Test
    void observedContextSharesTheExactPhysicalValueOfALocal() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue value = newObject();
                    ProtosClosureValue closure =
                            closure(fixture, "((v) => { x: v\n context })");
                    ProtosObjectValue observed =
                            assertInstanceOf(ProtosObjectValue.class, execute(fixture, closure, value));

                    assertSame(value, observed.readLocalSlot("x").orElseThrow());
                    List<String> names = operations(activationNode(closure));
                    assertTrue(names.contains("PublishCurrentClosureContext"), names::toString);
                    assertNoRetiredSelection(names);
                });
    }

    @Test
    void capturedCellKeepsOneIdentityAcrossSiblingsAndContextMutation() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue observed =
                            assertInstanceOf(
                                    ProtosObjectValue.class,
                                    execute(
                                            fixture,
                                            closure(
                                                    fixture,
                                                    "(() => { a: 1\n"
                                                            + " first: (() => a)\n"
                                                            + " second: (() => a)\n"
                                                            + " context })")));
                    ProtosClosureValue first =
                            assertInstanceOf(ProtosClosureValue.class, observed.readLocalSlot("first").orElseThrow());
                    ProtosClosureValue second =
                            assertInstanceOf(ProtosClosureValue.class, observed.readLocalSlot("second").orElseThrow());
                    ProtosLexicalCell cell = first.capturedCellsForRuntime()[0];
                    assertSame(cell, second.capturedCellsForRuntime()[0]);

                    observed.assignLocalSlot("a", 9L);
                    assertEquals(9L, execute(fixture, first));
                    assertEquals(9L, execute(fixture, second));
                    assertSame(cell, first.capturedCellsForRuntime()[0]);
                });
    }

    @Test
    void removalAndRecreationChangeActualMembershipOfLocalAndCell() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue observed =
                            assertInstanceOf(
                                    ProtosObjectValue.class,
                                    execute(
                                            fixture,
                                            closure(
                                                    fixture,
                                                    "(() => { local: 1\n captured: 2\n"
                                                            + " reader: (() => captured)\n"
                                                            + " context })")));
                    ProtosClosureValue reader =
                            assertInstanceOf(ProtosClosureValue.class, observed.readLocalSlot("reader").orElseThrow());
                    ProtosLexicalCell cell = reader.capturedCellsForRuntime()[0];

                    assertEquals(1L, observed.removeLocalSlot("local"));
                    assertEquals(2L, observed.removeLocalSlot("captured"));
                    assertFalse(observed.hasLocalSlot("local"));
                    assertFalse(observed.hasLocalSlot("captured"));
                    assertFalse(cell.isPresentForRuntime());

                    observed.createLocalSlot("local", 3L);
                    observed.createLocalSlot("captured", 4L);
                    assertEquals(3L, observed.readLocalSlot("local").orElseThrow());
                    assertSame(cell, reader.capturedCellsForRuntime()[0]);
                    assertEquals(4L, execute(fixture, reader));
                });
    }

    @Test
    void unobservedLocalReadStaysADirectLocalRead() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue closure = closure(fixture, "(() => { x: 1\n y: x\n y })");
                    List<String> names = operations(activationNode(closure));

                    assertTrue(names.stream().anyMatch(name -> name.startsWith("load.local")), names::toString);
                    assertFalse(names.contains("LoadLexicalLocal"), names::toString);
                    assertFalse(names.contains("Lookup"), names::toString);
                    assertTrue(names.stream().noneMatch(FRAME_PUBLICATION::contains), names::toString);
                    assertFalse(names.contains("CurrentActivation"), names::toString);
                    assertEquals(1L, execute(fixture, closure));
                });
    }

    @Test
    void admittedModuleReadKeepsReadGlobal() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue reader = closure(fixture, "x: 5\n(() => x)");
                    List<String> names = operations(activationNode(reader));

                    assertTrue(names.contains("ReadGlobal"), names::toString);
                    assertFalse(names.contains("Lookup"), names::toString);
                    assertTrue(names.stream().noneMatch(FRAME_PUBLICATION::contains), names::toString);
                    assertEquals(5L, execute(fixture, reader));
                });
    }

    @Test
    void ordinaryLocalAndCellReadsAreNeverNamedLexicalAccesses() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue closure =
                            closure(
                                    fixture,
                                    "(() => { a: 1\n b: 2\n get: (() => b)\n a + get() })");
                    List<String> names = operations(activationNode(closure));
                    ProtosClosureValue reader =
                            assertInstanceOf(
                                    ProtosClosureValue.class,
                                    execute(fixture, closure(fixture, "(() => { b: 2\n (() => b) })")));
                    List<String> readerNames = operations(activationNode(reader));

                    assertFalse(names.contains("Lookup"), names::toString);
                    assertFalse(readerNames.contains("Lookup"), readerNames::toString);
                    assertTrue(readerNames.contains("LoadCell"), readerNames::toString);
                    assertNoRetiredSelection(names);
                    assertNoRetiredSelection(readerNames);
                    assertEquals(3L, execute(fixture, closure));
                });
    }

    @Test
    void objectMemberReadingAnEnclosingParameterKeepsTheFastPath() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue value = newObject();
                    ProtosClosureValue make =
                            closure(fixture, "((name) => { d: { label: name }\n d.label })");
                    List<String> names = operations(activationNode(make));

                    assertSame(value, execute(fixture, make, value));
                    assertTrue(names.stream().noneMatch(FRAME_PUBLICATION::contains), names::toString);
                    assertNoRetiredSelection(names);
                });
    }

    @Test
    void sameNameMemberReadsTheEnclosingParameterBeforeItIsCreated() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue value = newObject();
                    ProtosClosureValue make =
                            closure(fixture, "((name) => { d: { name: name }\n d.name })");
                    List<String> names = operations(activationNode(make));

                    assertSame(value, execute(fixture, make, value));
                    assertTrue(names.contains("EnsureCurrentClosureLexicalView"), names::toString);
                    assertNoRetiredSelection(names);
                });
    }

    @Test
    void readAfterMemberEstablishmentSelectsTheNewMember() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue value = newObject();
                    ProtosObjectValue built =
                            assertInstanceOf(
                                    ProtosObjectValue.class,
                                    execute(
                                            fixture,
                                            closure(
                                                    fixture,
                                                    "((name) => { d: { before: name\n"
                                                            + " name: 7\n"
                                                            + " after: name }\n d })"),
                                            value));

                    assertSame(value, built.readLocalSlot("before").orElseThrow());
                    assertEquals(7L, built.readLocalSlot("name").orElseThrow());
                    assertEquals(7L, built.readLocalSlot("after").orElseThrow());
                });
    }

    @Test
    void successiveInvocationsDoNotShareConstructionLexicalStorage() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue first = newObject();
                    ProtosObjectValue second = newObject();
                    ProtosClosureValue make =
                            closure(fixture, "((name) => { d: { name: name }\n d })");

                    ProtosObjectValue firstBuilt =
                            assertInstanceOf(ProtosObjectValue.class, execute(fixture, make, first));
                    ProtosObjectValue secondBuilt =
                            assertInstanceOf(ProtosObjectValue.class, execute(fixture, make, second));

                    assertNotSame(firstBuilt, secondBuilt);
                    assertSame(first, firstBuilt.readLocalSlot("name").orElseThrow());
                    assertSame(second, secondBuilt.readLocalSlot("name").orElseThrow());
                });
    }

    @Test
    void capturedCellIsReadBeforeTheSameNameMemberIsCreated() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue value = newObject();
                    ProtosClosureValue make =
                            closure(
                                    fixture,
                                    "((name) => { reader: (() => name)\n"
                                            + " d: { name: name }\n"
                                            + " d.name })");
                    List<String> names = operations(activationNode(make));

                    assertSame(value, execute(fixture, make, value));
                    assertTrue(names.contains("CreateCell"), names::toString);
                    assertTrue(names.contains("EnsureCurrentClosureLexicalView"), names::toString);
                });
    }

    @Test
    void nestedConstructionKeepsTheGenuineEnclosingLexicalContext() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue value = newObject();
                    ProtosObjectValue outer =
                            assertInstanceOf(
                                    ProtosObjectValue.class,
                                    execute(
                                            fixture,
                                            closure(
                                                    fixture,
                                                    "((name) => { outer: { name: 1\n"
                                                            + " inner: { name: name } }\n outer })"),
                                            value));
                    ProtosObjectValue inner =
                            assertInstanceOf(ProtosObjectValue.class, outer.readLocalSlot("inner").orElseThrow());

                    // The enclosing object under construction is not a lexical scope.
                    assertSame(value, inner.readLocalSlot("name").orElseThrow());
                    assertEquals(1L, outer.readLocalSlot("name").orElseThrow());
                });
    }

    @Test
    void absentMemberWithoutEnclosingBindingSignalsTheLookupError() throws Exception {
        withFixture(
                fixture -> {
                    ProtosClosureValue make = closure(fixture, "(() => { d: { name: name }\n d })");

                    assertThrows(ProtosSignalException.class, () -> execute(fixture, make));
                });
    }

    @Test
    void closureCreatedInsideAnObjectDoesNotCaptureItsMembers() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue value = newObject();
                    ProtosObjectValue built =
                            assertInstanceOf(
                                    ProtosObjectValue.class,
                                    execute(
                                            fixture,
                                            closure(
                                                    fixture,
                                                    "((name) => { d: { name: 7\n get: (() => name) }\n d })"),
                                            value));
                    ProtosClosureValue get =
                            assertInstanceOf(ProtosClosureValue.class, built.readLocalSlot("get").orElseThrow());

                    assertSame(value, execute(fixture, get));
                });
    }

    @Test
    void progressGroupDescriptorShapeResolvesEveryEnclosingParameter() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue name = newObject();
                    ProtosObjectValue subtrees = newObject();
                    ProtosObjectValue directories = newObject();
                    ProtosObjectValue subdivided = newObject();
                    ProtosClosureValue progressGroupDescriptor =
                            closure(
                                    fixture,
                                    "((name, subtreeValues, directoryValues, subdivided) => {\n"
                                            + "    descriptor: {\n"
                                            + "        name: name\n"
                                            + "        subtrees: subtreeValues\n"
                                            + "        directories: directoryValues\n"
                                            + "        subdivided: subdivided\n"
                                            + "    }\n"
                                            + "    descriptor\n"
                                            + "})");
                    ProtosObjectValue descriptor =
                            assertInstanceOf(
                                    ProtosObjectValue.class,
                                    execute(
                                            fixture,
                                            progressGroupDescriptor,
                                            name,
                                            subtrees,
                                            directories,
                                            subdivided));

                    assertSame(name, descriptor.readLocalSlot("name").orElseThrow());
                    assertSame(subtrees, descriptor.readLocalSlot("subtrees").orElseThrow());
                    assertSame(directories, descriptor.readLocalSlot("directories").orElseThrow());
                    assertSame(subdivided, descriptor.readLocalSlot("subdivided").orElseThrow());
                });
    }

    private static void assertNoRetiredSelection(List<String> names) {
        assertTrue(
                names.stream().noneMatch(RETIRED_SELECTION::contains),
                () -> "retired storage selection emitted: " + names);
    }

    private static void assertNoCellOperation(List<String> names) {
        assertTrue(
                names.stream().noneMatch(CELL_OPERATIONS::contains),
                () -> "cell operation emitted without a captured binding: " + names);
    }

    /* Instruction names without the custom-operation prefix and quickening suffix. */
    private static List<String> operations(BytecodeNode node) {
        return node.getInstructionsAsList().stream()
                .map(ProtosInstructionNames::of)
                .map(name -> name.startsWith("c.") ? name.substring(2) : name)
                .map(name -> name.indexOf('$') < 0 ? name : name.substring(0, name.indexOf('$')))
                .toList();
    }

    private static BytecodeNode activationNode(ProtosClosureValue closure) {
        return closure.executionPlan()
                .orElseThrow()
                .bytecodeActivationRootForTesting()
                .getBytecodeNode();
    }

    private record Fixture(ProtosLanguage language, ProtosActivation module) {}

    private interface FixtureTest {
        void run(Fixture fixture) throws Exception;
    }

    private static void withFixture(FixtureTest test) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosStandardObjectProtocol.install();

                ProtosObjectValue contextPrototype = newObject();
                ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
                ProtosObjectValue errorPrototype = newObject();
                bindings.createLocalSlot("Context", contextPrototype);
                bindings.createLocalSlot("Error", errorPrototype);
                bindings.createLocalSlot("SlotNotFound", new ProtosObjectValue(errorPrototype));
                bindings.createLocalSlot("Array", newObject());
                bindings.freeze();

                test.run(
                        new Fixture(
                                LANGUAGE_REF.get(null),
                                new ProtosPrelude(bindings, contextPrototype).newModuleActivation()));
            } finally {
                context.leave();
            }
        }
    }

    /** Runs a module source whose final expression is the Closure under test. */
    private static ProtosClosureValue closure(Fixture fixture, String characters) {
        return assertInstanceOf(ProtosClosureValue.class, run(fixture, characters));
    }

    private static Object execute(Fixture fixture, ProtosClosureValue closure, Object... supplied) {
        ProtosActivation invocation =
                ProtosActivation.forClosureInvocation(
                        closure,
                        List.of(supplied),
                        fixture.module.prelude().orElseThrow(),
                        fixture.module.actorModuleState(),
                        fixture.module.currentModuleKey().orElse(null),
                        fixture.module.executionDomain());
        return closure.executionPlan().orElseThrow().executeBytecodeActivationForTesting(invocation);
    }

    private static Object run(Fixture fixture, String characters) {
        return lower(fixture.language, characters).getCallTarget().call(fixture.module);
    }

    private static ProtosBytecodeRootNode lower(ProtosLanguage language, String characters) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "i115-storage.protos").build();
        return new CanonicalToBytecodeLowerer(language, source).lowerRoot(canonicalize(characters));
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
    }

    private static Object slot(ProtosActivation module, String name) {
        return module.context().readLocalSlot(name).orElseThrow();
    }

    private static ProtosObjectValue newObject() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }
}
