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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosCaseOfProtocolTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    private static Object evaluate(ProtosPrelude prelude, String source) {
        return ProtosTestExecutionSupport.evaluate(
                source,
                prelude.newModuleActivation());
    }

    @Test
    void selectsFirstSuccessfulMatcherAndSpreadsCapture() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        42.caseOf(%{
                            41: () => false
                            Capture: value => value == 42
                        })
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void preservesSelectedCallableNullResult() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        1.caseOf(%{
                            1: () => null
                        })
                        """);

        assertSame(ProtosNullValue.INSTANCE, result);
    }

    @Test
    void doesNotEagerlyValidateUnreachedCallable() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        1.caseOf(%{
                            1: () => true
                            2: 99
                        })
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void snapshotsCaseAssociationsBeforeRunningMatchers() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        Second: {
                            match: (subject) => true
                        }

                        holder: Map()

                        First: {
                            match: (subject) => {
                                observedCases: holder["cases"]
                                observedCases[Second] = () => false
                                false
                            }
                        }

                        cases: %{
                            First: () => false
                            Second: () => true
                        }

                        holder["cases"] = cases
                        1.caseOf(cases)
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void rejectsInvalidReachedMatcherOutcome() throws Exception {
        ProtosPrelude prelude = core();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        evaluate(
                                prelude,
                                """
                                Bad: {
                                    match: (subject) => null
                                }

                                1.caseOf(%{
                                    Bad: () => true
                                })
                                """));
    }

    @Test
    void rejectsEmptyCaptureCarrierAsMatcherOutcome() throws Exception {
        ProtosPrelude prelude = core();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        evaluate(
                                prelude,
                                """
                                Bad: {
                                    match: (subject) => []
                                }

                                1.caseOf(%{
                                    Bad: () => true
                                })
                                """));
    }

    @Test
    void rejectsSelectedNonCallable() throws Exception {
        ProtosPrelude prelude = core();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        evaluate(
                                prelude,
                                """
                                1.caseOf(%{
                                    1: 99
                                })
                                """));
    }

    @Test
    void rejectsNonMapCarrierAndNoSuccessfulCase() throws Exception {
        ProtosPrelude prelude = core();

        assertThrows(
                ProtosSignalException.class,
                () -> evaluate(prelude, "1.caseOf([])"));

        assertThrows(
                ProtosSignalException.class,
                () ->
                        evaluate(
                                prelude,
                                """
                                1.caseOf(%{
                                    2: () => true
                                })
                                """));
    }
}
