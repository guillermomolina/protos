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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolDiscoverySignatureTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");

    @Test
    void projectsOrderedNamesWithoutInvokingBodies() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        TestValue: import("std:test/Test")

                        calls: {
                            value: 0
                        }

                        tests: Array(
                            TestValue("first", () => {
                                calls.value = calls.value + 1
                                11
                            }),
                            TestValue("second", () => {
                                calls.value = calls.value + 1
                                22
                            })
                        )

                        signature: Discovery.declarationSignature(tests)

                        (signature.size() == 2) &&
                            (signature[0] == "first") &&
                            (signature[1] == "second") &&
                            (calls.value == 0)
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void acceptsEmptyDeclaration() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        Discovery.declarationSignature(Array()).size() == 0
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void rejectsDuplicateNamesBeforeScheduling() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        TestValue: import("std:test/Test")

                        Discovery.declarationSignature(
                            Array(
                                TestValue("same", () => { null }),
                                TestValue("same", () => { null })
                            )
                        )
                        """);

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    @Test
    void rejectsNonArrayDeclarationRoot() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        Discovery.declarationSignature({})
                        """);

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    @Test
    void returnedSignatureIsFrozen() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        TestValue: import("std:test/Test")

                        signature:
                            Discovery.declarationSignature(
                                Array(TestValue("one", () => { null }))
                            )

                        signature[0] = "changed"
                        """);

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    @Test
    void rejectsInvalidSelectorWithoutNominalTestRecognition() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")

                        malformed: {
                            name: 42
                            call: () => { null }
                        }

                        Discovery.declarationSignature(Array(malformed))
                        """);

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    @Test
    void projectsOnlyExplicitLocalTestsRootFromModule() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        TestValue: import("std:test/Test")

                        module: {
                            unrelated: 42
                            tests: Array(
                                TestValue("first", () => { 1 }),
                                TestValue("second", () => { 2 })
                            )
                        }

                        signature:
                            Discovery.declarationSignatureFromModule(module)

                        (signature.size() == 2) &&
                            (signature[0] == "first") &&
                            (signature[1] == "second")
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void rejectsModuleWithoutLocalTestsRoot() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")

                        module: {
                            unrelated: 42
                        }

                        Discovery.declarationSignatureFromModule(module)
                        """);

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    @Test
    void rejectsInheritedTestsRoot() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        TestValue: import("std:test/Test")

                        base: {
                            tests: Array(
                                TestValue("inherited", () => { null })
                            )
                        }

                        module: base {
                            unrelated: 42
                        }

                        Discovery.declarationSignatureFromModule(module)
                        """);

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    @Test
    void acceptsExactRematerializedDeclarationSignature() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        TestValue: import("std:test/Test")

                        module: {
                            tests: Array(
                                TestValue("first", () => { 1 }),
                                TestValue("second", () => { 2 })
                            )
                        }

                        signature:
                            Discovery.requireDeclarationSignature(
                                module,
                                Array("first", "second")
                            )

                        (signature.size() == 2) &&
                            (signature[0] == "first") &&
                            (signature[1] == "second")
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void rejectsChangedRematerializedDeclarationSignatureBeforeBodyInvocation()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        TestValue: import("std:test/Test")

                        calls: {
                            value: 0
                        }

                        module: {
                            tests: Array(
                                TestValue("second", () => {
                                    calls.value = calls.value + 1
                                    2
                                }),
                                TestValue("first", () => {
                                    calls.value = calls.value + 1
                                    1
                                })
                            )
                        }

                        failed: false

                        Error.handle(
                            () => {
                                Discovery.requireDeclarationSignature(
                                    module,
                                    Array("first", "second")
                                )
                                null
                            },
                            (error) => {
                                failed = true
                                null
                            }
                        )

                        failed && (calls.value == 0)
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void rejectsAddedOrRemovedSelectorFromRematerializedSignature()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        TestValue: import("std:test/Test")

                        missingModule: {
                            tests: Array(
                                TestValue("first", () => { null })
                            )
                        }

                        extraModule: {
                            tests: Array(
                                TestValue("first", () => { null }),
                                TestValue("second", () => { null }),
                                TestValue("third", () => { null })
                            )
                        }

                        missingFailed: false
                        extraFailed: false

                        Error.handle(
                            () => {
                                Discovery.requireDeclarationSignature(
                                    missingModule,
                                    Array("first", "second")
                                )
                                null
                            },
                            (error) => {
                                missingFailed = true
                                null
                            }
                        )

                        Error.handle(
                            () => {
                                Discovery.requireDeclarationSignature(
                                    extraModule,
                                    Array("first", "second")
                                )
                                null
                            },
                            (error) => {
                                extraFailed = true
                                null
                            }
                        )

                        missingFailed && extraFailed
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void rejectsUnknownSelectedSelectorBeforeAnyBodyInvocation() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        """
                        Discovery: import("self:Discovery")
                        TestValue: import("std:test/Test")

                        calls: {
                            value: 0
                        }

                        module: {
                            tests: Array(
                                TestValue("first", () => {
                                    calls.value = calls.value + 1
                                    1
                                }),
                                TestValue("second", () => {
                                    calls.value = calls.value + 1
                                    2
                                })
                            )
                        }

                        failed: false

                        Error.handle(
                            () => {
                                Discovery.resolveSelectedTest(
                                    module,
                                    Array("first", "second"),
                                    "missing"
                                )
                                null
                            },
                            (error) => {
                                failed = true
                                null
                            }
                        )

                        failed && (calls.value == 0)
                        """);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    private static ProtosExecutionOutcome execute(String source) throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        SHARED_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));

        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        return ProtosTestExecutionSupport.execute(
                source,
                prelude.newModuleActivation());
    }
}
