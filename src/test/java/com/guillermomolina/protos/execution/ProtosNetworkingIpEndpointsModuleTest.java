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

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * LIB005-C IpEndpoints harness. Observable endpoint parsing/formatting behavior
 * is owned by ordinary Protos fixtures. Java only supplies real Core/std
 * bootstrap and observes the exact local module export surface.
 */
final class ProtosNetworkingIpEndpointsModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "library", "network", "ip-endpoints");

    @Test
    void importedModuleExportsExactlyApprovedSurface() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        Object imported =
                new ProtosSourceCompiler()
                        .compile("import(\"std:network/IpEndpoints\")")
                        .call(prelude.newModuleActivation());
        ProtosObjectValue module = assertInstanceOf(ProtosObjectValue.class, imported);

        assertEquals(Set.of("parse", "format"), module.localSlotsSnapshot().keySet());
    }

    @Test
    void endpointParsingFormattingAndRoundTripConform() throws Exception {
        assertFixture("parse-format.protos");
    }

    @Test
    void malformedEndpointTextAndWrongDomainsFailClosed() throws Exception {
        assertFixture("invalid.protos");
    }

    private static void assertFixture(String fixture) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        Object result =
                new ProtosSourceCompiler()
                        .compile(
                                Files.readString(
                                        CASE_ROOT.resolve(fixture),
                                        StandardCharsets.UTF_8))
                        .call(prelude.newModuleActivation());

        assertSame(ProtosBooleanValue.TRUE, result, fixture);
    }
}
