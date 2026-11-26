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

import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosObjectMatchProtocolTest {
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
    void sendsEqualityToMatcherExactlyOnceAndNeverReversesIt() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        state: {
                            matcherCalls: 0
                            subjectCalls: 0
                        }

                        MatcherBase: {
                            equalTo: (other) => {
                                state.matcherCalls = state.matcherCalls + 1
                                true
                            }
                        }
                        Matcher: MatcherBase.alias("equalTo", "==")

                        SubjectBase: {
                            equalTo: (other) => {
                                state.subjectCalls = state.subjectCalls + 1
                                false
                            }
                        }
                        Subject: SubjectBase.alias("equalTo", "==")

                        matched: Matcher.match(Subject)

                        matched &&
                            state.matcherCalls == 1 &&
                            state.subjectCalls == 0
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void doesNotUseIdentityShortcutBeforeEquality() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        state: {
                            calls: 0
                        }

                        MatcherBase: {
                            equalTo: (other) => {
                                state.calls = state.calls + 1
                                false
                            }
                        }
                        Matcher: MatcherBase.alias("equalTo", "==")

                        matched: Matcher.match(Matcher)

                        matched == false && state.calls == 1
                        """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void returnsEqualityResultUnchanged() throws Exception {
        ProtosPrelude prelude = core();

        Object result =
                evaluate(
                        prelude,
                        """
                        MatcherBase: {
                            equalTo: (other) => null
                        }
                        Matcher: MatcherBase.alias("equalTo", "==")

                        Matcher.match(Object)
                        """);

        assertSame(ProtosNullValue.INSTANCE, result);
    }

    // PERF044: ordinary == runs through the direct equality operation; its
    // standard Number law and every non-standard result are unchanged.

    @Test
    void perf044DirectNumberEqualityKeepsTheExactNumericLaw() throws Exception {
        assertSame(Boolean.TRUE, evaluate(core(), """
                nan: 0.0 / 0.0
                inf: 1.0 / 0.0
                big: 9223372036854775807 + 1
                (1 == 1) && ((1 == 2) == false) &&
                    (1.5 == 1.5) && (1 == 1.0) && (1.0 == 1) &&
                    ((nan == nan) == false) && (0.0 == -0.0) &&
                    (inf == inf) && ((inf == 9223372036854775807) == false) &&
                    ((9007199254740993 == 9007199254740992.0) == false) &&
                    (9007199254740992 == 9007199254740992.0) &&
                    (big == big) && ((big == 9223372036854775807) == false) &&
                    ((1 == "1") == false) && ((1 == null) == false)
                """));
    }

    @Test
    void perf044CustomEqualityResultIsReturnedUnvalidated() throws Exception {
        assertSame(Boolean.TRUE, evaluate(core(), """
                OddBase: { equalTo: (other) => 7 }
                Odd: OddBase.alias("equalTo", "==")
                Odd2: Odd()
                (Odd2 == 1) == 7
                """));
    }

    @Test
    void perf044StandaloneMatchReturnsTheExactMatcherResult() throws Exception {
        assertSame(Boolean.TRUE, evaluate(core(), """
                Seven: { match: (subject) => 7 }
                pattern: Seven
                subject: 1
                (pattern.match(subject) == 7) && (1.match(1) == true) &&
                    (1.match(1.0) == true) && (Any.match(subject) == true)
                """));
    }

    @Test
    void perf044RedefinedObjectMatchIsSelected() throws Exception {
        assertSame(Boolean.TRUE, evaluate(core(), """
                Thing: {}
                thing: Thing()
                before: thing.match(thing)
                Thing.createSlot("match", (subject) => "custom")
                (before == true) && (thing.match(1) == "custom")
                """));
    }
}
