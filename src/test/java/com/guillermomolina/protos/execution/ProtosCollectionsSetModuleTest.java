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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosCollectionsSetModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void setModuleConstructsFreshMapBackedSetsAndExposesCanonicalMarkers()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Set: import("std:collections/Set")
                        emptyA: Set()
                        emptyB: Set()
                        values: Set("Ada", "Grace")
                        Array(
                            Set.size(emptyA),
                            emptyA === emptyB,
                            Set.size(values),
                            Set.contains(values, "Ada"),
                            Set.contains(values, "Linus"),
                            values.at("Ada") === true,
                            values
                        )
                        """);

        assertInteger(BigInteger.ZERO, result.indexedAt(BigInteger.ZERO));
        assertSame(ProtosBooleanValue.FALSE, result.indexedAt(BigInteger.ONE));
        assertInteger(BigInteger.TWO, result.indexedAt(BigInteger.TWO));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(3)));
        assertSame(ProtosBooleanValue.FALSE, result.indexedAt(BigInteger.valueOf(4)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(5)));
        assertInstanceOf(ProtosMapValue.class, result.indexedAt(BigInteger.valueOf(6)));
    }

    @Test
    void setConstructionUsesMapEqualityAndKeepsTheFirstRepresentative()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        Set: import("std:collections/Set")
                        values: Set(1, 1.0)
                        representative: null
                        marker: null
                        values.each((key, value) => {
                            representative = key
                            marker = value
                        })
                        Array(
                            Set.size(values),
                            Set.contains(values, 1.0),
                            representative === 1,
                            representative === 1.0,
                            marker === true
                        )
                        """);

        assertInteger(BigInteger.ONE, result.indexedAt(BigInteger.ZERO));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ONE));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO));
        assertSame(ProtosBooleanValue.FALSE, result.indexedAt(BigInteger.valueOf(3)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(4)));
    }

    @Test
    void identitySetConstructionUsesSemanticIdentityRatherThanMapEquality()
            throws Exception {
        ProtosArrayValue result =
                runArray(
                        """
                        IdentitySet: import("std:collections/IdentitySet")
                        values: IdentitySet(1, 1.0, 1)
                        Array(
                            IdentitySet.size(values),
                            IdentitySet.contains(values, 1),
                            IdentitySet.contains(values, 1.0),
                            values.at(1) === true,
                            values.at(1.0) === true,
                            values
                        )
                        """);

        assertInteger(BigInteger.TWO, result.indexedAt(BigInteger.ZERO));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.ONE));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.TWO));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(3)));
        assertSame(ProtosBooleanValue.TRUE, result.indexedAt(BigInteger.valueOf(4)));
        assertInstanceOf(
                ProtosIdentityMapValue.class,
                result.indexedAt(BigInteger.valueOf(5)));
    }

    @Test
    void realStandardImportsRemainCachedPerActorAndActorLocal() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();
        var actorA = prelude.newModuleActivation();
        var actorB = prelude.newModuleActivation();

        Object first = compiler.compile("import(\"std:collections/Set\")").call(actorA);
        Object repeated = compiler.compile("import(\"std:collections/Set\")").call(actorA);
        Object otherActor = compiler.compile("import(\"std:collections/Set\")").call(actorB);

        assertSame(first, repeated);
        assertNotSame(first, otherActor);
    }

    @Test
    void obsoleteLowercaseCollectionModuleSpellingsDoNotRemainAsAliases()
            throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        for (String specifier :
                new String[] {
                    "std:collections/set", "std:collections/identity_set"
                }) {
            assertThrows(
                    ProtosSignalException.class,
                    () ->
                            new ProtosSourceCompiler()
                                    .compile("import(\"" + specifier + "\")")
                                    .call(prelude.newModuleActivation()),
                    specifier);
        }
    }

    private static ProtosArrayValue runArray(String source) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        Object result =
                new ProtosSourceCompiler()
                        .compile(source)
                        .call(prelude.newModuleActivation());
        return assertInstanceOf(ProtosArrayValue.class, result);
    }

    private static void assertInteger(BigInteger expected, Object value) {
        assertEquals(
                expected,
                assertInstanceOf(ProtosIntegerValue.class, value).value());
    }
}
