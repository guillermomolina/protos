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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosCoreErrors.StandardError;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosCoreErrorInfrastructureTest {
    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    /**
     * Java-side boundary coverage retained for exact prototype parentage and the
     * typed runtime failure factory. Source-level signaling/construction behavior
     * is covered in executable Protos; current source parent() reflection used by
     * the attempted migration signals before these parentage assertions can run.
     */
    @Test
    void standardTaxonomyAndFailureFactoryRemainJavaSide() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertParent(prelude, "Error", "Object");
        assertParent(prelude, "InvalidReturn", "Error");
        assertParent(prelude, "SlotNotFound", "Error");
        assertParent(prelude, "InvalidSuper", "Error");
        assertParent(prelude, "Cancelled", "Error");
        assertParent(prelude, "FutureResolutionCycle", "Error");
        assertParent(prelude, "RequestOutcomeUncertain", "Error");
        assertParent(prelude, "NonTransferableValue", "Error");
        assertParent(prelude, "NonParallelValue", "Error");
        assertParent(prelude, "InvalidPredicateResult", "Error");
        assertParent(prelude, "InvalidComparatorResult", "Error");
        assertParent(prelude, "InvalidComparatorOrder", "Error");
        assertParent(prelude, "ParallelRegionOverlap", "Error");
        assertParent(prelude, "ParallelRegionInUse", "Error");
        assertParent(prelude, "ParallelRegionOutsideP", "Error");
        assertParent(prelude, "IOError", "Error");
        assertParent(prelude, "InvalidIOArgument", "IOError");
        assertParent(prelude, "IOLifecycleError", "IOError");
        assertParent(prelude, "IOCapacityExhausted", "IOError");
        assertParent(prelude, "EncodingError", "IOError");
        assertParent(prelude, "LineTooLong", "IOError");

        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue genericA = ProtosCoreErrors.newError(activation);
        ProtosObjectValue genericB = ProtosCoreErrors.newError(activation);
        assertNotSame(genericA, genericB);
        assertSame(prelude.errorPrototype(), genericA.parent().orElseThrow());
        assertSame(prelude.errorPrototype(), genericB.parent().orElseThrow());

        ProtosObjectValue slotA =
                ProtosCoreErrors.newOccurrence(activation, StandardError.SLOT_NOT_FOUND);
        ProtosObjectValue slotB =
                ProtosCoreErrors.newOccurrence(activation, StandardError.SLOT_NOT_FOUND);
        assertNotSame(slotA, slotB);
        Object slotPrototype =
                prelude.bindings().readLocalSlot("SlotNotFound").orElseThrow();
        assertSame(slotPrototype, slotA.parent().orElseThrow());
        assertSame(slotPrototype, slotB.parent().orElseThrow());

        ProtosObjectValue invalidReturn =
                ProtosCoreErrors.newInvalidReturn(activation);
        Object invalidReturnPrototype =
                prelude.bindings().readLocalSlot("InvalidReturn").orElseThrow();
        assertSame(invalidReturnPrototype, invalidReturn.parent().orElseThrow());
    }

    @Test
    void signalingPreservesExactErrorObject() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue error = ProtosCoreErrors.newError(activation);

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        error, "signal", List.of(), activation));

        assertSame(error, signal.error());
    }

    @Test
    void internalSignalApiDoesNotCoerceNonErrors() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue nonError =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosCoreErrors.signal(activation, nonError));
    }

    private static void assertParent(
            ProtosPrelude prelude, String child, String parent) {
        ProtosObjectValue childPrototype =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        prelude.bindings().readLocalSlot(child).orElseThrow());
        Object expected =
                "Object".equals(parent)
                        ? ProtosObjectValue.rootObject()
                        : prelude.bindings().readLocalSlot(parent).orElseThrow();
        assertSame(expected, childPrototype.parent().orElseThrow());
    }
}
