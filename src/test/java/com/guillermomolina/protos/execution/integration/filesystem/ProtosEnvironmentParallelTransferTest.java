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

import com.guillermomolina.protos.runtime.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosEnvironmentParallelTransferTest {
    @Test
    void environmentSnapshotHasOrdinaryPValueTransferWithFreshIdentityAndAliasing()
            throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue prototype = ProtosStandardEnvironmentProtocol.createPrototype();

        ProtosProcessRuntime process =
                new ProtosProcessRuntime(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze());
        process.establishEnvironmentForRuntime(
                prototype,
                exactDomain(),
                List.of(new ProtosEnvironmentValue.NativeEntry("A", "one")));
        ProtosEnvironmentValue source = process.environmentSnapshotForRuntime().orElseThrow();

        Class<?> transfer =
                Class.forName("com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer");
        Method copy =
                transfer.getDeclaredMethod(
                        "copy", Object.class, ProtosActivation.class, IdentityHashMap.class);
        copy.setAccessible(true);

        IdentityHashMap<Object, Object> memo = new IdentityHashMap<>();
        ProtosEnvironmentValue first =
                assertInstanceOf(
                        ProtosEnvironmentValue.class,
                        copy.invoke(null, source, activation, memo));
        ProtosEnvironmentValue second =
                assertInstanceOf(
                        ProtosEnvironmentValue.class,
                        copy.invoke(null, source, activation, memo));

        assertNotSame(source, first);
        assertSame(first, second);
        assertFalse(ProtosIdentity.identical(source, first));
        assertEquals("one", first.getForRuntime("A").orElseThrow().value());
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactDomain() {
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
