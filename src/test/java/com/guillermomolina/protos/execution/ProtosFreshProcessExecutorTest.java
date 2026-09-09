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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosProcessCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.source.Source;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosFreshProcessExecutorTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void eachExecutionCreatesASeparateSemanticProcess() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosExecutionOutcome first =
                ProtosFreshProcessExecutor.execute(
                        request(prelude, source("process.args()")));
        ProtosExecutionOutcome second =
                ProtosFreshProcessExecutor.execute(
                        request(prelude, source("process.args()")));

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, first.state());
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, second.state());
        assertNotNull(first.value());
        assertNotNull(second.value());
        assertFalse(
                ProtosIdentity.identical(first.value(), second.value()),
                "distinct fresh Processes must not share one canonical args snapshot identity");
    }

    @Test
    void rootTaskSupportsRealFutureSuspensionAndReturnsInertCompletion() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosExecutionOutcome outcome =
                ProtosFreshProcessExecutor.execute(
                        request(
                                prelude,
                                source("(() => { 42 }).future().value()")));

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        ProtosIntegerValue value =
                assertInstanceOf(ProtosIntegerValue.class, outcome.value());
        assertEquals(BigInteger.valueOf(42), value.value());
        assertNull(outcome.error());
    }

    @Test
    void semanticErrorIsReturnedAsFailedOutcomeInsteadOfEscapingAsHostFailure()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosExecutionOutcome outcome =
                ProtosFreshProcessExecutor.execute(
                        request(
                                prelude,
                                source("1.definitelyMissing()")));

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
        assertNull(outcome.value());
        assertNotNull(outcome.error());
    }

    @Test
    void processIsTerminatedBeforeOutcomeReturns() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosExecutionOutcome outcome =
                ProtosFreshProcessExecutor.execute(
                        request(prelude, source("process")));

        ProtosProcessCapabilityValue capability =
                assertInstanceOf(ProtosProcessCapabilityValue.class, outcome.value());
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                capability.processForRuntime().lifecycleState());
    }

    private static ProtosFreshProcessExecutor.Request request(
            ProtosPrelude prelude,
            Source entry) {
        return new ProtosFreshProcessExecutor.Request(
                prelude,
                entry,
                List.of("alpha", "beta"),
                exactEnvironmentDomain(),
                List.of(
                        new ProtosEnvironmentValue.NativeEntry("A", "one"),
                        new ProtosEnvironmentValue.NativeEntry("B", "two")),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static Source source(String characters) {
        return Source.newBuilder(ProtosLanguage.ID, characters, "<fresh-process-test>")
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactEnvironmentDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }
}
