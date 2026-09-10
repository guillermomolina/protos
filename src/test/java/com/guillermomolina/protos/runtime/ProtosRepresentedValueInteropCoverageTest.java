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

import com.oracle.truffle.api.interop.InteropLibrary;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosRepresentedValueInteropCoverageTest {
    private static final Path RUNTIME_SOURCE =
            Path.of("src/main/java/com/guillermomolina/protos/runtime");

    private static final Pattern DIRECT_REPRESENTED_VALUE =
            Pattern.compile(
                    "public\\s+final\\s+class\\s+(Protos[A-Za-z0-9_]+)\\s+"
                            + "implements\\s+ProtosRepresentedValue\\b");

    private final InteropLibrary interop = InteropLibrary.getUncached();

    @Test
    void everyDirectRepresentedValueOwnsExplicitInteropAndSafeDisplay()
            throws Exception {
        int found = 0;
        try (Stream<Path> files = Files.list(RUNTIME_SOURCE)) {
            for (Path path :
                    files.filter(p -> p.getFileName().toString().endsWith(".java"))
                            .toList()) {
                String source = Files.readString(path);
                if (!DIRECT_REPRESENTED_VALUE.matcher(source).find()) {
                    continue;
                }
                found++;
                assertTrue(
                        source.contains("@ExportLibrary(InteropLibrary.class)"),
                        () -> path + " must explicitly export InteropLibrary");
                assertTrue(
                        source.contains("toDisplayString("),
                        () -> path + " must explicitly own a bounded display");
            }
        }
        assertTrue(found > 0, "direct represented-value source inventory unexpectedly empty");
    }

    @Test
    void representativeOpaqueDirectValuesExposeOnlyBoundedObjectDisplay()
            throws Exception {
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();

        ProtosPathValue path =
                new ProtosPathValue(prototype, false, List.of());

        ProtosEncodingValue encoding =
                ProtosEncodingValue.portableForRuntime(
                        prototype, ProtosEncodingValue.PortableKind.UTF8);

        ProtosEnvironmentValue environment =
                ProtosEnvironmentValue.captureForRuntime(
                        prototype,
                        new ProtosEnvironmentValue.NativeNameDomain() {
                            @Override
                            public boolean sameCapturedName(String left, String right) {
                                return left.equals(right);
                            }

                            @Override
                            public boolean isQueryRepresentable(String portableName) {
                                return true;
                            }

                            @Override
                            public boolean matchesQuery(
                                    String capturedNativeName, String portableName) {
                                return capturedNativeName.equals(portableName);
                            }
                        },
                        List.of());

        ProtosGroupRefValue groupRef =
                ProtosGroupRefValue.acquireForRuntime(
                        prototype, UUID.randomUUID(), UUID.randomUUID());

        for (Object value : List.of(path, encoding, environment, groupRef)) {
            assertEquals("Object", interop.toDisplayString(value, false));
            assertFalse(interop.hasMembers(value));
            assertFalse(interop.hasArrayElements(value));
            assertFalse(interop.isExecutable(value));
            assertFalse(interop.isString(value));
            assertFalse(interop.isNumber(value));
            assertFalse(interop.isBoolean(value));
            assertFalse(interop.isNull(value));
        }
    }
}
