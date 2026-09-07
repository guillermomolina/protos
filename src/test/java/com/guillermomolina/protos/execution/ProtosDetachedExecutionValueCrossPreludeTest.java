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

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosDetachedExecutionValueCrossPreludeTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void genericErrorOccurrenceGetsDestinationErrorPrototype() throws Exception {
        Pair pair = pair();

        ProtosObjectValue sourceError = ProtosCoreErrors.newError(pair.sourceActivation);
        ProtosObjectValue copied =
                (ProtosObjectValue)
                        ProtosDetachedExecutionValue.snapshot(
                                sourceError,
                                pair.sourcePrelude,
                                pair.destinationActivation);

        assertNotSame(sourceError, copied);
        assertSame(pair.destinationPrelude.errorPrototype(), copied.parent().orElseThrow());
        assertNotSame(pair.sourcePrelude.errorPrototype(), copied.parent().orElseThrow());
    }

    @Test
    void namedStandardErrorOccurrenceKeepsCategoryInDestinationPrelude() throws Exception {
        Pair pair = pair();

        ProtosObjectValue sourceError =
                ProtosCoreErrors.newOccurrence(
                        pair.sourceActivation,
                        ProtosCoreErrors.StandardError.SLOT_NOT_FOUND);
        ProtosObjectValue copied =
                (ProtosObjectValue)
                        ProtosDetachedExecutionValue.snapshot(
                                sourceError,
                                pair.sourcePrelude,
                                pair.destinationActivation);

        assertNotSame(sourceError, copied);
        assertSame(
                ProtosCoreErrors.prototype(
                        pair.destinationActivation,
                        ProtosCoreErrors.StandardError.SLOT_NOT_FOUND),
                copied.parent().orElseThrow());
        assertNotSame(sourceError.parent().orElseThrow(), copied.parent().orElseThrow());
    }

    @Test
    void explicitStandardErrorPrototypeMapsToDestinationPrototype() throws Exception {
        Pair pair = pair();

        Object copied =
                ProtosDetachedExecutionValue.snapshot(
                        pair.sourcePrelude.errorPrototype(),
                        pair.sourcePrelude,
                        pair.destinationActivation);

        assertSame(pair.destinationPrelude.errorPrototype(), copied);
        assertNotSame(pair.sourcePrelude.errorPrototype(), copied);
    }

    private static Pair pair() throws Exception {
        ProtosPrelude sourcePrelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude destinationPrelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        return new Pair(
                sourcePrelude,
                sourcePrelude.newModuleActivation(),
                destinationPrelude,
                destinationPrelude.newModuleActivation());
    }

    private record Pair(
            ProtosPrelude sourcePrelude,
            ProtosActivation sourceActivation,
            ProtosPrelude destinationPrelude,
            ProtosActivation destinationActivation) {}
}
