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

package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosStandardEnvironmentProtocol;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosEnvironmentSnapshotTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void processOwnsOneCanonicalEnvironmentSnapshotAndDetachesHostList() throws Exception {
        ProtosObjectValue prototype = ProtosStandardEnvironmentProtocol.createPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        ArrayList<ProtosEnvironmentValue.NativeEntry> host = new ArrayList<>();
        host.add(new ProtosEnvironmentValue.NativeEntry("A", "one"));

        assertEquals(
                ProtosProcessRuntime.EnvironmentSnapshotState.AVAILABLE,
                process.establishEnvironmentForRuntime(prototype, exactDomain(), host));

        ProtosEnvironmentValue first = process.environmentSnapshotForRuntime().orElseThrow();
        ProtosEnvironmentValue second = process.environmentSnapshotForRuntime().orElseThrow();
        assertSame(first, second);
        assertTrue(ProtosIdentity.identical(first, second));
        assertEquals("one", first.getForRuntime("A").orElseThrow().value());

        host.set(0, new ProtosEnvironmentValue.NativeEntry("A", "changed"));
        host.add(new ProtosEnvironmentValue.NativeEntry("B", "later"));
        assertEquals(
                "one",
                process.environmentSnapshotForRuntime()
                        .orElseThrow()
                        .getForRuntime("A")
                        .orElseThrow()
                        .value());
        assertFalse(process.environmentSnapshotForRuntime().orElseThrow().containsForRuntime("B"));
    }

    @Test
    void duplicateEquivalentNativeNamesMakeAcquisitionStablyInvalid() {
        ProtosObjectValue prototype = ProtosStandardEnvironmentProtocol.createPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());

        assertEquals(
                ProtosProcessRuntime.EnvironmentSnapshotState.INVALID,
                process.establishEnvironmentForRuntime(
                        prototype,
                        asciiCaseInsensitiveDomain(),
                        List.of(
                                new ProtosEnvironmentValue.NativeEntry("Path", "one"),
                                new ProtosEnvironmentValue.NativeEntry("PATH", "two"))));
        assertTrue(process.environmentSnapshotForRuntime().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> process.establishEnvironmentForRuntime(prototype, exactDomain(), List.of()));
        assertEquals(
                ProtosProcessRuntime.EnvironmentSnapshotState.INVALID,
                process.environmentSnapshotStateForRuntime());
    }

    @Test
    void distinctProcessesHaveDistinctCanonicalSnapshotIdentity() {
        ProtosObjectValue prototype = ProtosStandardEnvironmentProtocol.createPrototype();
        ProtosProcessRuntime firstProcess = new ProtosProcessRuntime(actorRefPrototype());
        ProtosProcessRuntime secondProcess = new ProtosProcessRuntime(actorRefPrototype());

        firstProcess.establishEnvironmentForRuntime(
                prototype,
                exactDomain(),
                List.of(new ProtosEnvironmentValue.NativeEntry("A", "1")));
        secondProcess.establishEnvironmentForRuntime(
                prototype,
                exactDomain(),
                List.of(new ProtosEnvironmentValue.NativeEntry("A", "1")));

        ProtosEnvironmentValue first = firstProcess.environmentSnapshotForRuntime().orElseThrow();
        ProtosEnvironmentValue second = secondProcess.environmentSnapshotForRuntime().orElseThrow();

        assertNotSame(first, second);
        assertFalse(ProtosIdentity.identical(first, second));
    }

    @Test
    void actorTransferCreatesFreshDestinationIdentityAndPreservesAliases() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue prototype = ProtosStandardEnvironmentProtocol.createPrototype();
        ProtosProcessRuntime process = new ProtosProcessRuntime(actorRefPrototype());
        process.establishEnvironmentForRuntime(
                prototype,
                exactDomain(),
                List.of(new ProtosEnvironmentValue.NativeEntry("A", "1")));
        ProtosEnvironmentValue source = process.environmentSnapshotForRuntime().orElseThrow();

        List<Object> copied =
                ProtosActorValueTransfer.snapshotArguments(List.of(source, source), activation);

        ProtosEnvironmentValue destination =
                assertInstanceOf(ProtosEnvironmentValue.class, copied.get(0));
        assertNotSame(source, destination);
        assertSame(destination, copied.get(1));
        assertFalse(ProtosIdentity.identical(source, destination));
        assertEquals("1", destination.getForRuntime("A").orElseThrow().value());
    }

    @Test
    void scalarComparatorUsesUnicodeCodePointsRatherThanUtf16Units() {
        String bmp = String.valueOf((char) 0xE000);
        String supplementary = new String(Character.toChars(0x10000));

        assertTrue(ProtosEnvironmentValue.compareUnicodeScalarsForTesting(bmp, supplementary) < 0);
        assertTrue(ProtosEnvironmentValue.compareUnicodeScalarsForTesting(supplementary, bmp) > 0);
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    static ProtosEnvironmentValue.NativeNameDomain exactDomain() {
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

    static ProtosEnvironmentValue.NativeNameDomain asciiCaseInsensitiveDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equalsIgnoreCase(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                if (name.contains("=") || name.indexOf('\0') >= 0) {
                    return false;
                }
                for (int index = 0; index < name.length(); index++) {
                    if (name.charAt(index) > 0x7f) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equalsIgnoreCase(query);
            }
        };
    }
}
