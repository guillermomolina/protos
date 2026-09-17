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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CanonicalMapConstructionExecutionTest {
    @Test
    void directAstBackendExecutesMapConstruction() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                new ProtosSourceCompiler()
                        .compile("%{ \"answer\": 42 }[\"answer\"] == 42")
                        .call(activation);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void directAstBackendRejectsDuplicateInitialKey() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosSourceCompiler()
                                .compile("%{ \"key\": 1; \"key\": 2 }")
                                .call(activation));
    }

    @Test
    void directAstBackendUsesInheritedFactoryAndBypassesAtPut()
            throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                new ProtosSourceCompiler()
                        .compile(
                                "DerivedMap: Map {\n"
                                        + "    marker: 7\n"
                                        + "    atPut: (key, value) => value\n"
                                        + "}\n"
                                        + "Map: DerivedMap\n"
                                        + "mapping: %{ \"first\": 1; \"second\": 2 }\n"
                                        + "(mapping.marker == 7) && "
                                        + "(mapping[\"first\"] == 1) && "
                                        + "(mapping[\"second\"] == 2)")
                        .call(activation);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void directAstBackendRejectsArbitraryShadowFactory()
            throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosSourceCompiler()
                                .compile(
                                        "Map: () => { "
                                                + "{ atPut: (key, value) => value } "
                                                + "}\n"
                                                + "%{ \"key\": 1 }")
                                .call(activation));
    }
}
