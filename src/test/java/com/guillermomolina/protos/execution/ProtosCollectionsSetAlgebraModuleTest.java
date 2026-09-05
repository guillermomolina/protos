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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosCollectionsSetAlgebraModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void setAlgebraReturnsFreshOpenResultsWithDeterministicLeftDerivedOrder()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Set: import("std:collections/Set")
                        left: Set(1, 2, 3)
                        right: Set(2.0, 4, 1.0)

                        union: Set.union(left, right)
                        intersection: Set.intersection(left, right)
                        difference: Set.difference(left, right)

                        unionOrder: Map()
                        unionIndex: 0
                        Set.each(union, (element) => {
                            unionOrder.atPut(unionIndex, element)
                            unionIndex = unionIndex + 1
                        })

                        intersectionOrder: Map()
                        intersectionIndex: 0
                        Set.each(intersection, (element) => {
                            intersectionOrder.atPut(intersectionIndex, element)
                            intersectionIndex = intersectionIndex + 1
                        })

                        differenceOrder: Map()
                        differenceIndex: 0
                        Set.each(difference, (element) => {
                            differenceOrder.atPut(differenceIndex, element)
                            differenceIndex = differenceIndex + 1
                        })

                        added: Set.add(union, 5)

                        Array(
                            union !== left,
                            union !== right,
                            intersection !== left,
                            difference !== left,
                            Set.size(left),
                            Set.size(right),
                            unionIndex,
                            unionOrder.at(0) === 1,
                            unionOrder.at(1) === 2,
                            unionOrder.at(2) === 3,
                            unionOrder.at(3) === 4,
                            intersectionIndex,
                            intersectionOrder.at(0) === 1,
                            intersectionOrder.at(1) === 2,
                            differenceIndex,
                            differenceOrder.at(0) === 3,
                            added === union,
                            Set.contains(union, 5),
                            union.at(1) === true,
                            intersection.at(1) === true,
                            difference.at(3) === true
                        )
                        """);

        for (int index : new int[] {0, 1, 2, 3}) {
            assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(index)));
        }
        assertInteger(3, result.indexedAt(BigInteger.valueOf(4)));
        assertInteger(3, result.indexedAt(BigInteger.valueOf(5)));
        assertInteger(4, result.indexedAt(BigInteger.valueOf(6)));
        for (int index : new int[] {7, 8, 9, 10}) {
            assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(index)));
        }
        assertInteger(2, result.indexedAt(BigInteger.valueOf(11)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(12)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(13)));
        assertInteger(1, result.indexedAt(BigInteger.valueOf(14)));
        for (int index : new int[] {15, 16, 17, 18, 19, 20}) {
            assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(index)));
        }
    }

    @Test
    void identitySetAlgebraPreservesIdentityMembershipAndOrder() throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        IdentitySet: import("std:collections/IdentitySet")
                        left: IdentitySet(1, 2)
                        right: IdentitySet(1.0, 2, 3)

                        union: IdentitySet.union(left, right)
                        intersection: IdentitySet.intersection(left, right)
                        difference: IdentitySet.difference(left, right)

                        order: Map()
                        index: 0
                        IdentitySet.each(union, (element) => {
                            order.atPut(index, element)
                            index = index + 1
                        })

                        Array(
                            IdentitySet.size(union),
                            order.at(0) === 1,
                            order.at(1) === 2,
                            order.at(2) === 1.0,
                            order.at(3) === 3,
                            IdentitySet.size(intersection),
                            IdentitySet.contains(intersection, 2),
                            IdentitySet.contains(intersection, 1),
                            IdentitySet.size(difference),
                            IdentitySet.contains(difference, 1),
                            IdentitySet.sameMembers(IdentitySet(1), IdentitySet(1.0)),
                            IdentitySet.isDisjoint(IdentitySet(1), IdentitySet(1.0))
                        )
                        """);

        assertInteger(4, result.indexedAt(BigInteger.ZERO));
        for (int index : new int[] {1, 2, 3, 4}) {
            assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(index)));
        }
        assertInteger(1, result.indexedAt(BigInteger.valueOf(5)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(6)));
        assertSame(ProtosBooleanValue.FALSE, result.indexedAt(BigInteger.valueOf(7)));
        assertInteger(1, result.indexedAt(BigInteger.valueOf(8)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(9)));
        assertSame(ProtosBooleanValue.FALSE, result.indexedAt(BigInteger.valueOf(10)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(11)));
    }

    @Test
    void predicatesAreExplicitMembershipRelationsNotMapEquality() throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Set: import("std:collections/Set")
                        left: Set(1, 2)
                        same: Set(2.0, 1.0)

                        Array(
                            Set.sameMembers(left, same),
                            left == same,
                            Set.isSubset(Set(1), left),
                            Set.isSubset(Set(1, 3), left),
                            Set.isSuperset(left, Set(2.0)),
                            Set.isDisjoint(Set(3), left),
                            Set.isDisjoint(Set(2.0), left),
                            Set.sameMembers(Set(1), Set(1, 2))
                        )
                        """);

        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ZERO));
        assertSame(ProtosBooleanValue.FALSE, result.indexedAt(BigInteger.ONE));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO));
        assertSame(ProtosBooleanValue.FALSE, result.indexedAt(BigInteger.valueOf(3)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(4)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(5)));
        assertSame(ProtosBooleanValue.FALSE, result.indexedAt(BigInteger.valueOf(6)));
        assertSame(ProtosBooleanValue.FALSE, result.indexedAt(BigInteger.valueOf(7)));
    }

    @Test
    void predicatesShortCircuitBeforeLaterMapKeySearch() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        AtomicInteger badHashCalls = new AtomicInteger();
        ProtosObjectValue badKey = new ProtosObjectValue(ProtosObjectValue.rootObject());
        badKey.createLocalSlot(
                "hash",
                ProtosClosureValue.nativeClosure(
                        (callActivation, supplied) -> {
                            badHashCalls.incrementAndGet();
                            return new ProtosIntegerValue(BigInteger.valueOf(97));
                        }));

        ProtosMapValue left = prelude.newMap();
        ProtosMapValue right = prelude.newMap();
        ProtosMapValue empty = prelude.newMap();
        ProtosIntegerValue one = new ProtosIntegerValue(BigInteger.ONE);

        ProtosInvocation.invokeMessage(
                left, "atPut", List.of(one, ProtosBooleanValue.TRUE), activation);
        ProtosInvocation.invokeMessage(
                left, "atPut", List.of(badKey, ProtosBooleanValue.TRUE), activation);
        ProtosInvocation.invokeMessage(
                right,
                "atPut",
                List.of(new ProtosIntegerValue(BigInteger.ONE), ProtosBooleanValue.TRUE),
                activation);

        badHashCalls.set(0);
        activation.context().createLocalSlot("left", left);
        activation.context().createLocalSlot("right", right);
        activation.context().createLocalSlot("empty", empty);

        ProtosArrayValue result =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        new ProtosSourceCompiler()
                                .compile(
                                        """
                                        Set: import("std:collections/Set")
                                        Array(
                                            Set.sameMembers(left, right),
                                            Set.isSubset(left, empty),
                                            Set.isSuperset(empty, left),
                                            Set.isDisjoint(left, right)
                                        )
                                        """)
                                .call(activation));

        for (int index = 0; index < 4; index++) {
            assertSame(
                    ProtosBooleanValue.FALSE,
                    result.indexedAt(BigInteger.valueOf(index)));
        }
        assertEquals(0, badHashCalls.get());
    }

    @Test
    void setDataTransfersAcrossActorsWhileModuleBehaviorRemainsActorLocal()
            throws Exception {
        for (String module : new String[] {"Set", "IdentitySet"}) {
            ProtosStandardLibraryModuleResolver resolver =
                    new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
            ProtosActivation activation = prelude.newModuleActivation();
            ProtosSourceCompiler compiler = new ProtosSourceCompiler();

            Object moduleValue =
                    compiler.compile("import(\"std:collections/" + module + "\")")
                            .call(activation);
            assertThrows(
                    ProtosSignalException.class,
                    () -> ProtosActorValueTransfer.snapshotValue(moduleValue, activation));

            Object data =
                    compiler.compile(
                                    """
                                    Collection: import("std:collections/%s")
                                    Collection.union(Collection(1, 2), Collection(2, 3))
                                    """
                                            .formatted(module))
                            .call(activation);
            ProtosObjectValue copied =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            ProtosActorValueTransfer.snapshotValue(data, activation));

            assertNotSame(data, copied);
            assertFalse(copied.isClosed());
            assertFalse(copied.isFrozen());

            if (module.equals("Set")) {
                ProtosMapValue copiedMap =
                        assertInstanceOf(ProtosMapValue.class, copied);
                assertSame(prelude.mapPrototype(), copiedMap.parent().orElseThrow());
                assertEquals(3, copiedMap.keyedSize());
                copiedMap.keyedSnapshot()
                        .forEach(entry -> assertSame(ProtosBooleanValue.TRUE, entry.value()));
            } else {
                ProtosIdentityMapValue copiedMap =
                        assertInstanceOf(ProtosIdentityMapValue.class, copied);
                assertSame(prelude.identityMapPrototype(), copiedMap.parent().orElseThrow());
                assertEquals(3, copiedMap.keyedSize());
                copiedMap.keyedSnapshot()
                        .forEach(entry -> assertSame(ProtosBooleanValue.TRUE, entry.value()));
            }
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

    private static void assertInteger(long expected, Object value) {
        assertEquals(
                BigInteger.valueOf(expected),
                assertInstanceOf(ProtosIntegerValue.class, value).value());
    }
}
