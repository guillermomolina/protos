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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosCollectionsSetMutationModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void addAndRemoveReturnTheExactReceiverAndPreserveCanonicalMarkers()
            throws Exception {
        for (String module : new String[] {"Set", "IdentitySet"}) {
            ProtosArrayValue result =
                    runArray(
                            String.format(
                                    """
                                    Collection: import("std:collections/%1$s")
                                    values: Collection(1, 2)
                                    added: Collection.add(values, 3)
                                    removed: Collection.remove(values, 2)
                                    Array(
                                        added === values,
                                        removed === values,
                                        Collection.size(values),
                                        Collection.contains(values, 1),
                                        Collection.contains(values, 2),
                                        Collection.contains(values, 3),
                                        values.at(1) === true,
                                        values.at(3) === true
                                    )
                                    """,
                                    module));

            assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ZERO), module);
            assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ONE), module);
            assertInteger(BigInteger.TWO, result.indexedAt(BigInteger.TWO));
            assertSame(
                    ProtosBooleanValue.TRUE,
                    result.indexedAt(BigInteger.valueOf(3)),
                    module);
            assertSame(
                    ProtosBooleanValue.FALSE,
                    result.indexedAt(BigInteger.valueOf(4)),
                    module);
            assertSame(
                    ProtosBooleanValue.TRUE,
                    result.indexedAt(BigInteger.valueOf(5)),
                    module);
            assertSame(
                    ProtosBooleanValue.TRUE,
                    result.indexedAt(BigInteger.valueOf(6)),
                    module);
            assertSame(
                    ProtosBooleanValue.TRUE,
                    result.indexedAt(BigInteger.valueOf(7)),
                    module);
        }
    }

    @Test
    void setAddKeepsTheExistingEqualRepresentativeAndInsertionPosition()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Set: import("std:collections/Set")
                        values: Set(1)
                        same: Set.add(values, 1.0)
                        representative: null
                        marker: null
                        values.each((key, value) => {
                            representative = key
                            marker = value
                        })
                        Array(
                            same === values,
                            Set.size(values),
                            representative === 1,
                            representative === 1.0,
                            marker === true
                        )
                        """);

        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ZERO));
        assertInteger(BigInteger.ONE, result.indexedAt(BigInteger.ONE));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO));
        assertSame(ProtosBooleanValue.FALSE, result.indexedAt(BigInteger.valueOf(3)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(4)));
    }

    @Test
    void mutationStateIsExactlyTheUnderlyingMapState() throws Exception {
        for (String module : new String[] {"Set", "IdentitySet"}) {
            ProtosArrayValue allowed =
                    runWithState(
                            module,
                            false,
                            """
                            added: Collection.add(values, 1)
                            Array(
                                added === values,
                                Collection.size(values),
                                Collection.contains(values, 1),
                                values.at(1) === true
                            )
                            """);

            assertSame(ProtosBooleanValue.TRUE, allowed.indexedAt(BigInteger.ZERO), module);
            assertInteger(BigInteger.ONE, allowed.indexedAt(BigInteger.ONE));
            assertSame(ProtosBooleanValue.TRUE, allowed.indexedAt(BigInteger.TWO), module);
            assertSame(
                    ProtosBooleanValue.TRUE,
                    allowed.indexedAt(BigInteger.valueOf(3)),
                    module);

            assertStateSignals(module, false, "Collection.add(values, 2)");
            assertStateSignals(module, false, "Collection.remove(values, 1)");
            assertStateSignals(module, true, "Collection.add(values, 1)");
            assertStateSignals(module, true, "Collection.remove(values, 1)");
        }
    }

    @Test
    void eachVisitsOneMemberArgumentInInsertionOrderFromAShallowSnapshot()
            throws Exception {
        for (String module : new String[] {"Set", "IdentitySet"}) {
            ProtosArrayValue result =
                    runArray(
                            String.format(
                                    """
                                    Collection: import("std:collections/%1$s")
                                    values: Collection(1, 2)
                                    visited: Map()
                                    index: 0
                                    same: Collection.each(values, (element) => {
                                        visited.atPut(index, element)
                                        (index == 0).ifTrue() {
                                            Collection.add(values, 3)
                                            Collection.remove(values, 2)
                                        }
                                        index = index + 1
                                    })
                                    Array(
                                        same === values,
                                        index,
                                        visited.at(0) === 1,
                                        visited.at(1) === 2,
                                        Collection.size(values),
                                        Collection.contains(values, 2),
                                        Collection.contains(values, 3)
                                    )
                                    """,
                                    module));

            assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ZERO), module);
            assertInteger(BigInteger.TWO, result.indexedAt(BigInteger.ONE));
            assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO), module);
            assertSame(
                    ProtosBooleanValue.TRUE,
                    result.indexedAt(BigInteger.valueOf(3)),
                    module);
            assertInteger(BigInteger.TWO, result.indexedAt(BigInteger.valueOf(4)));
            assertSame(
                    ProtosBooleanValue.FALSE,
                    result.indexedAt(BigInteger.valueOf(5)),
                    module);
            assertSame(
                    ProtosBooleanValue.TRUE,
                    result.indexedAt(BigInteger.valueOf(6)),
                    module);
        }
    }

    @Test
    void eachDefersUserCallbackValidationUntilActualInvocation() throws Exception {
        for (String module : new String[] {"Set", "IdentitySet"}) {
            ProtosArrayValue empty =
                    runArray(
                            String.format(
                                    """
                                    Collection: import("std:collections/%1$s")
                                    values: Collection()
                                    same: Collection.each(values, 42)
                                    Array(same === values, Collection.size(values))
                                    """,
                                    module));

            assertSame(ProtosBooleanValue.TRUE, empty.indexedAt(BigInteger.ZERO), module);
            assertInteger(BigInteger.ZERO, empty.indexedAt(BigInteger.ONE));

            assertSignals(
                    String.format(
                            """
                            Collection: import("std:collections/%1$s")
                            Collection.each(Collection(1), 42)
                            """,
                            module));
            assertSignals(
                    String.format(
                            """
                            Collection: import("std:collections/%1$s")
                            Collection.each(Collection(1), (element, marker) => {
                                null
                            })
                            """,
                            module));
            assertSignals(
                    String.format(
                            """
                            Collection: import("std:collections/%1$s")
                            Collection.each(Collection(1, 2), (element) => {
                                Map().at("missing")
                            })
                            """,
                            module));
        }
    }

    @Test
    void removeOfAnAbsentMemberPropagatesTheUnderlyingMapError() {
        for (String module : new String[] {"Set", "IdentitySet"}) {
            assertSignals(
                    String.format(
                            """
                            Collection: import("std:collections/%1$s")
                            Collection.remove(Collection(), 1)
                            """,
                            module));
        }
    }

    private static Object run(String source) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        return new ProtosSourceCompiler()
                .compile(source)
                .call(prelude.newModuleActivation());
    }

    private static ProtosArrayValue runArray(String source) throws Exception {
        return assertInstanceOf(ProtosArrayValue.class, run(source));
    }

    private static ProtosArrayValue runWithState(
            String module, boolean frozen, String body) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue values =
                module.equals("Set")
                        ? new ProtosMapValue(prelude.mapPrototype())
                        : new ProtosIdentityMapValue(prelude.identityMapPrototype());

        ProtosInvocation.invokeMessage(
                values,
                "atPut",
                List.of(
                        new ProtosIntegerValue(BigInteger.ONE),
                        ProtosBooleanValue.TRUE),
                activation);
        if (frozen) {
            values.freeze();
        } else {
            values.close();
        }
        activation.context().createLocalSlot("values", values);

        Object result =
                new ProtosSourceCompiler()
                        .compile(
                                "Collection: import(\"std:collections/"
                                        + module
                                        + "\")\n"
                                        + body)
                        .call(activation);
        return assertInstanceOf(ProtosArrayValue.class, result);
    }

    private static void assertStateSignals(
            String module, boolean frozen, String body) {
        assertThrows(
                ProtosSignalException.class,
                () ->
                        runWithState(
                                module,
                                frozen,
                                body + "\nArray()\n"));
    }

    private static void assertSignals(String source) {
        assertThrows(ProtosSignalException.class, () -> run(source));
    }

    private static void assertInteger(BigInteger expected, Object value) {
        assertEquals(
                expected,
                assertInstanceOf(ProtosIntegerValue.class, value).value());
    }
}
